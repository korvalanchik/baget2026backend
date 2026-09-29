package com.example.baget.stocktake;
import java.math.BigDecimal;
import java.util.*;
public final class StocktakeRules {
 private StocktakeRules() {}
 public record Reservation(long id,long productId,long orderNo,BigDecimal demand,BigDecimal reserved) {}
 public record Allocation(long id,long productId,long orderNo,BigDecimal demand,BigDecimal before,BigDecimal after,
                          BigDecimal shortageBefore,BigDecimal shortageAfter) {}
 /** Preserve existing reservations first, oldest first. Only then distribute any free stock to unfilled demand. */
 public static List<Allocation> allocate(BigDecimal stock,List<Reservation> reservations){
  if(stock==null || stock.signum()<0)throw new IllegalArgumentException("Count required");
  var rows=reservations.stream().sorted(Comparator.comparingLong(Reservation::id)).toList();
  var after=new HashMap<Long,BigDecimal>();BigDecimal free=stock;
  for(var r:rows){
   if(r.demand()==null || r.reserved()==null || r.demand().signum()<0 || r.reserved().signum()<0 || r.reserved().compareTo(r.demand())>0 || after.containsKey(r.id()))throw new IllegalArgumentException("Invalid reservation");
   var keep=r.reserved().min(free);after.put(r.id(),keep);free=free.subtract(keep);
  }
  for(var r:rows){var keep=after.get(r.id());var add=r.demand().subtract(keep).min(free);after.put(r.id(),keep.add(add));free=free.subtract(add);}
  return rows.stream().map(r->new Allocation(r.id(),r.productId(),r.orderNo(),r.demand(),r.reserved(),after.get(r.id()),r.demand().subtract(r.reserved()),r.demand().subtract(after.get(r.id())))).toList();
 }
 public static BigDecimal difference(BigDecimal before,BigDecimal counted){return before==null||counted==null?null:counted.subtract(before);}
 public static boolean same(BigDecimal a,BigDecimal b){return a==null?b==null:b!=null&&a.compareTo(b)==0;}
}
