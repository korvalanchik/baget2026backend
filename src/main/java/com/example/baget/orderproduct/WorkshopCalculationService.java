package com.example.baget.orderproduct;

import com.example.baget.calculation.*;
import com.example.baget.calculation.estimate.*;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.calculation.preview.PartPriceResolver;
import com.example.baget.calculation.product.*;
import com.example.baget.parts.*;
import com.example.baget.users.UsersRepository;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class WorkshopCalculationService {
    private final JdbcTemplate db;
    private final CalculatedOrderService orders;
    private final UsersRepository users;
    private final EstimatePreviewService preview;
    private final MaterialConsumptionService consumption;
    private final PartsRepository parts;
    private final ObjectMapper json;
    private final Validator validator;
    private final PartPriceResolver prices;
    public WorkshopCalculationService(JdbcTemplate db,CalculatedOrderService orders,UsersRepository users,
            EstimatePreviewService preview,MaterialConsumptionService consumption,PartsRepository parts,ObjectMapper json,Validator validator,PartPriceResolver prices) {
        this.db=db;this.orders=orders;this.users=users;this.preview=preview;this.consumption=consumption;this.parts=parts;this.json=json;this.validator=validator;this.prices=prices;
    }
    public enum Basis { ARTWORK, OUTER }
    public record Extra(@Positive Long sourceItemNo,@Positive long partNo,@NotNull Basis basis,
                        @Min(1) @Max(10000) int unitsPerProduct) {}
    public record Request(@Positive long expectedRevision,
        @NotNull @Positive @DecimalMax("100000") @Digits(integer=6,fraction=3) BigDecimal widthMm,
        @NotNull @Positive @DecimalMax("100000") @Digits(integer=6,fraction=3) BigDecimal heightMm,
        @Size(max=200) List<@NotNull @Valid Extra> extras) {}
    public record Line(Long itemNo,int selectionIndex,long partNo,String description,String role,String unit,
                       BigDecimal quantity,BigDecimal widthMm,BigDecimal heightMm,String note,
                       List<CrossbarCalculator.Segment> segmentsPerProduct) {}
    public record Result(long revision,BigDecimal widthMm,BigDecimal heightMm,int productQuantity,
                         BigDecimal outerWidthMm,BigDecimal outerHeightMm,boolean canApply,List<Line> lines,List<String> warnings) {}
    private record Key(int component,String role,long partNo,int occurrence) {}
    private record Candidate(Key key,ProductPreviewResponse.Line line,EstimatePreviewRequest.Item request) {}

    public Result calculate(long orderNo,long pid,Request r,Authentication a) {
        if(r==null || !validator.validate(r).isEmpty())throw error("Перевірте обмір: розміри до 100000 мм і додаткові позиції");
        if(a==null || !a.isAuthenticated())throw new PreviewException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","Увійдіть до системи");
        var user=users.findByUsername(a.getName()).orElseThrow(()->error("Користувача не знайдено"));
        if(user.getRoles().stream().noneMatch(v->Set.of("ROLE_ADMIN","ROLE_FOREMAN").contains(v.getName())))
            throw new PreviewException(HttpStatus.FORBIDDEN,"FOREMAN_REQUIRED","Розрахунок обміру доступний майстру або адміністратору");
        var order=orders.read(orderNo,a); // Shared order lock, revision and branch checks.
        if(order.revision()!=r.expectedRevision())throw error("Замовлення змінилося. Оновіть картку");
        var rows=db.queryForList("SELECT configuration,calculation,production_state,production_confirmed,active_rework_id FROM order_products WHERE id=? AND order_no=? AND active=1",pid,orderNo);
        if(rows.isEmpty())throw error("Виріб не належить цьому замовленню");var product=rows.get(0);
        if(!Set.of("ACCEPTED","IN_PROGRESS").contains(product.get("production_state")) || yes(product.get("production_confirmed")) || Set.of(5,10,12).contains(order.statusOrder()))
            throw error("Перерахунок доступний для прийнятого виробу в роботі або відкритого доопрацювання");
        boolean rework=product.get("active_rework_id")!=null;
        if(rework!=(r.extras()!=null))throw error("Для доопрацювання передайте тільки обрані додаткові позиції; для звичайної роботи — без extras");
        var configuration=tree(product.get("configuration"));
        var components=configuration.path("draft").path("components");
        if(!components.isArray() || components.isEmpty())throw error("Немає збереженої конфігурації калькулятора");
        var requests=new ArrayList<EstimatePreviewRequest.Item>();int component=0;
        for(var node:components) {
            ObjectNode copy=node.deepCopy();copy.put("clientItemId","measure-"+component++);
            var context=(ObjectNode)(copy.hasNonNull("product")?copy.get("product"):copy.get("mounting"));
            if(context==null)throw error("Некоректна збережена конфігурація");
            context.put("widthMm",r.widthMm());context.put("heightMm",r.heightMm());
            requests.add(convert(copy,EstimatePreviewRequest.Item.class));
        }
        ProductAttachmentValidation.validate(requests);
        var pricingAuth=prices.consumptionOnly(a); // Only quantities/dimensions leave this method; monetary preview fields are discarded.
        var computed=preview.preview(new EstimatePreviewRequest(requests),pricingAuth);
        var original=convert(tree(product.get("calculation")),EstimatePreviewResponse.class);
        if(original.items().size()!=requests.size())throw error("Збережений розрахунок не узгоджений зі складовими виробу");
        var before=flatten(original,requests);var after=flatten(computed,requests);
        var current=new LinkedHashMap<Key,Candidate>();for(var c:after)current.put(c.key(),c);
        var persisted=db.queryForList("SELECT ItemNo AS itemNo,PartNo AS partNo,calculation_role AS role,calculation_unit AS unit FROM items WHERE OrderNo=? AND order_product_id=? AND rework_id IS NULL ORDER BY ItemNo",orderNo,pid);
        if(persisted.size()!=before.size())throw error("Початкові позиції не узгоджені зі збереженим розрахунком");
        var sourceKeys=new HashMap<Long,Key>();var itemByKey=new HashMap<Key,Long>();
        for(int i=0;i<before.size();i++) {
            var old=before.get(i);var item=persisted.get(i);
            if(number(item.get("partNo"))!=old.key().partNo() || !old.key().role().equals(item.get("role")) || !old.line().calculation().unit().name().equals(item.get("unit")))
                throw error("Порядок початкових позицій змінено; потрібна перевірка складу");
            long itemNo=number(item.get("itemNo"));sourceKeys.put(itemNo,old.key());itemByKey.put(old.key(),itemNo);
        }
        int count=requests.get(0).product()!=null?requests.get(0).product().productQuantity():requests.get(0).mounting().productQuantity();
        for(var request:requests)if((request.product()!=null?request.product().productQuantity():request.mounting().productQuantity())!=count)throw error("Кількість екземплярів складових не узгоджена");
        BigDecimal outerW=r.widthMm(),outerH=r.heightMm();
        for(var request:requests)if(request.product()!=null && request.product().matMargins()!=null){outerW=SheetComposition.width(request.product());outerH=SheetComposition.height(request.product());break;}
        var result=new ArrayList<Line>();var warnings=new ArrayList<String>();boolean canApply=true;
        if(!rework) {
            for(var old:before) {
                var candidate=current.get(old.key());long itemNo=itemByKey.get(old.key());
                result.add(candidate==null?new Line(itemNo,-1,old.key().partNo(),old.line().calculation().description(),old.key().role(),old.line().calculation().unit().name(),BigDecimal.ZERO,null,null,"За новим обміром позиція більше не потрібна",List.of()):line(itemNo,-1,candidate));
            }
            for(var fresh:after)if(!itemByKey.containsKey(fresh.key())) {
                result.add(line(null,-1,fresh));canApply=false;
                warnings.add("Після зміни розміру потрібна інша/додаткова позиція: "+fresh.line().calculation().description()+". Узгодьте зміну складу; автоматична підміна артикула не виконується.");
            }
        } else {
            int index=0;
            for(var extra:r.extras()) {
                if(extra.sourceItemNo()!=null) {
                    var source=db.queryForList("SELECT PartNo FROM items WHERE OrderNo=? AND ItemNo=? AND order_product_id=?",Long.class,orderNo,extra.sourceItemNo(),pid);
                    if(source.isEmpty() || source.get(0)!=extra.partNo())throw error("Додаткова позиція не належить цьому виробу");
                }
                Key key=extra.sourceItemNo()==null?null:sourceKeys.get(extra.sourceItemNo());
                if(key!=null) {
                    var candidate=current.get(key);
                    if(candidate==null) {
                        result.add(new Line(extra.sourceItemNo(),index,extra.partNo(),"Позиція "+extra.partNo(),key.role(),"",BigDecimal.ZERO,null,null,"За новим обміром не потрібна: приберіть додатковий рядок",List.of()));
                    } else result.add(line(extra.sourceItemNo(),index,candidate));
                } else {
                    // A new article has no trustworthy usage context. User explicitly chooses geometry and multiplicity.
                    var part=parts.findById(extra.partNo()).orElseThrow(()->error("Матеріал не знайдено"));
                    if(extra.partNo()==27970L || extra.partNo()==28030L)
                        throw error("Полотно/перемичку розраховуйте з початкової позиції виробу або введіть витрату вручну: потрібен контекст натяжки/підрамника");
                    var w=extra.basis()==Basis.OUTER?outerW:r.widthMm();var h=extra.basis()==Basis.OUTER?outerH:r.heightMm();
                    ConsumptionResult calculation;
                    try {calculation=consumption.calculate(part,new CalculationContext(w,h,count,BigDecimal.valueOf(extra.unitsPerProduct())));}
                    catch(IllegalArgumentException e){throw error("Не вдалося розрахувати "+part.getDescription()+": "+e.getMessage());}
                    // For area/length formulas, this is layers or identical repetitions. QUANTITY already applies it.
                    var qty=part.getCalculationMethod()==CalculationMethod.QUANTITY?calculation.totalConsumption():calculation.totalConsumption().multiply(BigDecimal.valueOf(extra.unitsPerProduct()));
                    var size=WorkshopGeometry.size(part.getCalculationMethod(),part.getUnitType(),w,h,part.getCalculationParams(),part.getProfilWidth()==null?null:BigDecimal.valueOf(part.getProfilWidth()));
                    result.add(new Line(extra.sourceItemNo(),index,extra.partNo(),part.getDescription(),"REWORK",calculation.unit().name(),qty,size.widthMm(),size.heightMm(),size.note()+"; повторів на екземпляр: "+extra.unitsPerProduct(),List.of()));
                    warnings.add("Для "+part.getDescription()+" застосовано формулу матеріалу та обрані вами розмір/повтори. Спеціальні правила складу автоматично не додаються.");
                }
                index++;
            }
            warnings.add("Розраховано тільки вибрані додаткові позиції. Для початкової позиції враховано всі її шари та екземпляри; при частковій заміні зменште кількість вручну.");
        }
        warnings.add("Це розрахункова витрата за фактичним обміром. Перевірте розкрій, відходи та реальну кількість перед збереженням. Ціни виробу не переоцінюються.");
        return new Result(order.revision(),r.widthMm(),r.heightMm(),count,outerW,outerH,canApply,List.copyOf(result),List.copyOf(warnings));
    }
    private List<Candidate> flatten(EstimatePreviewResponse response,List<EstimatePreviewRequest.Item> requests) {
        var result=new ArrayList<Candidate>();
        for(int i=0;i<response.items().size();i++) {
            var component=response.items().get(i);var list=component.product()!=null?component.product().lines():component.mounting().lines();
            var occurrences=new HashMap<String,Integer>();
            for(var line:list) {
                var c=line.calculation();String token=line.role()+":"+c.partNo();int occurrence=occurrences.merge(token,1,Integer::sum);
                result.add(new Candidate(new Key(i,line.role(),c.partNo(),occurrence),line,requests.get(i)));
            }
        }
        return result;
    }
    private Line line(Long itemNo,int index,Candidate c) {
        var calculation=c.line().calculation();var request=c.request();
        var part=parts.findById(calculation.partNo()).orElseThrow(()->error("Матеріал не знайдено"));
        BigDecimal width=request.product()!=null?SheetComposition.width(request.product()):request.mounting().widthMm();
        BigDecimal height=request.product()!=null?SheetComposition.height(request.product()):request.mounting().heightMm();
        WorkshopGeometry.Size size;
        if("CANVAS".equals(c.line().role())) {
            var mounting=request.mounting();
            size=WorkshopGeometry.canvas(width,height,mounting.mountingType()==com.example.baget.calculation.mounting.MountingPreviewRequest.MountingType.UNDERFRAME,mounting.canvasType()==com.example.baget.calculation.mounting.MountingPreviewRequest.CanvasType.PRINT);
        } else size=WorkshopGeometry.size(calculation.calculationMethod(),calculation.unit(),width,height,part.getCalculationParams(),part.getProfilWidth()==null?null:BigDecimal.valueOf(part.getProfilWidth()));
        return new Line(itemNo,index,calculation.partNo(),calculation.description(),c.line().role(),calculation.unit().name(),calculation.totalConsumption(),size.widthMm(),size.heightMm(),size.note(),c.line().segmentsPerProduct());
    }
    private JsonNode tree(Object value) {try{return json.readTree(value.toString());}catch(Exception e){throw error("Некоректний збережений JSON виробу");}}
    private <T> T convert(JsonNode node,Class<T> type) {try{return json.treeToValue(node,type);}catch(Exception e){throw error("Збережена конфігурація не відповідає калькулятору");}}
    private boolean yes(Object value) {return Boolean.TRUE.equals(value)||value instanceof Number n&&n.intValue()==1;}
    private long number(Object value) {return ((Number)value).longValue();}
    private static PreviewException error(String message) {return new PreviewException(HttpStatus.CONFLICT,"WORKSHOP_CALCULATION_CONFLICT",message);}
}
