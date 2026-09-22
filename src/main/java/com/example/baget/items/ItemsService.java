package com.example.baget.items;

import com.example.baget.orders.Orders;
import com.example.baget.orders.OrdersRepository;
import com.example.baget.parts.PartLookupService;
import com.example.baget.parts.PartsDTO;
import com.example.baget.util.NotFoundException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
public class ItemsService {

    private final com.example.baget.orderproduct.LegacyOrderGuard legacyGuard;
    private final ItemsRepository itemsRepository;
    private final PartLookupService partLookupService;
    private final OrdersRepository ordersRepository;

    public ItemsService(final ItemsRepository itemsRepository, PartLookupService partLookupService,
                        OrdersRepository ordersRepository, com.example.baget.orderproduct.LegacyOrderGuard legacyGuard) {
        this.legacyGuard = legacyGuard;
        this.itemsRepository = itemsRepository;
        this.partLookupService = partLookupService;
        this.ordersRepository = ordersRepository;
    }

    public List<ItemsDTO> findAll() {
        final List<Items> items = itemsRepository.findAll(Sort.by("order.orderNo"));
        return items.stream()
                .map(item -> mapItemsToDTO(item, new ItemsDTO()))
                .toList();
    }

    public List<ItemsDTO> findByOrderNo(Long orderNo) {
        final List<Items> items = itemsRepository.findByOrderOrderNo(orderNo);

//        partLookupService.findAll().stream()
//                .filter(p -> p.getPartNo() == null || p.getDescription() == null)
//                .forEach(p -> System.out.println("Invalid part: " + p));

        // Отримуємо всі Parts з кешу разом
        Map<Long, String> partDescriptionMap = partLookupService.findAll().stream()
                .collect(Collectors.toMap(PartsDTO::getPartNo, PartsDTO::getDescription));

        return items.stream()
                .map(item -> {
                    ItemsDTO dto = mapItemsToDTO(item, new ItemsDTO());
                    // Вставляємо опис з кешу
                    dto.setDescription(partDescriptionMap.get(item.getPartNo()));
                    return dto;
                })
                .toList();
    }


    public ItemsDTO get(final Long orderNo, final Long itemNo) {
        ItemId itemId = new ItemId(orderNo, itemNo);
        return itemsRepository.findById(itemId)
                .map(items -> mapItemsToDTO(items, new ItemsDTO()))
                .orElseThrow(NotFoundException::new);
    }

    @Transactional
    public void create(final ItemsDTO itemsDTO) {
        legacyGuard.check(itemsDTO.getOrderNo());
        Long orderNo = itemsDTO.getOrderNo();
        Long maxItemNo = itemsRepository.findMaxItemNoByOrderNo(orderNo);
        Long newItemNo = (maxItemNo != null) ? maxItemNo + 1 : 1L;
        itemsDTO.setItemNo(newItemNo);
        final Items items = new Items();
        Orders order = ordersRepository.findById(orderNo).orElseThrow(() -> new EntityNotFoundException("Order not found"));
        items.setOrder(order);

        mapItemsToEntity(itemsDTO, items);
        itemsRepository.save(items);
    }

    @Transactional
    public void update(final Long orderNo, final Long itemNo, final ItemsDTO itemsDTO) {
        legacyGuard.check(orderNo);
        legacyGuard.check(itemsDTO.getOrderNo());
        ItemId itemId = new ItemId(orderNo, itemNo);
        final Items items = itemsRepository.findById(itemId).orElseThrow(NotFoundException::new);
        Orders order = ordersRepository.findById(orderNo).orElseThrow(() -> new EntityNotFoundException("Order not found"));
        items.setOrder(order);
        mapItemsToEntity(itemsDTO, items);
        itemsRepository.save(items);
    }

    @Transactional
    public void delete(final Long orderNo, final Long itemNo) {
        legacyGuard.check(orderNo);
        itemsRepository.deleteItemByOrderNoAndItemNo(orderNo, itemNo);
    }

    public ItemsDTO mapItemsToDTO(final Items items, final ItemsDTO itemsDTO) {
        if (items == null) {
            return null;
        }
        itemsDTO.setItemNo(items.getId().getItemNo());
        itemsDTO.setOrderNo(items.getId().getOrderNo());
        itemsDTO.setPartNo(items.getPartNo());
        itemsDTO.setProfilWidth(items.getProfilWidth());
        itemsDTO.setEstimationWidth(items.getEstimationWidth());
        itemsDTO.setEstimationHeight(items.getEstimationHeight());
        itemsDTO.setWidth(items.getWidth());
        itemsDTO.setHeight(items.getHeight());
        itemsDTO.setQty(items.getQty());
        itemsDTO.setQuantity(items.getQuantity());
        itemsDTO.setSellPrice(items.getSellPrice());
        itemsDTO.setResellerPrice(items.getResellerPrice());
        itemsDTO.setDiscount(items.getDiscount());
        itemsDTO.setOnHand(items.getOnHand());
        itemsDTO.setCost(items.getCost());
        itemsDTO.setSum(items.getSum());
        return itemsDTO;
    }

    public void mapItemsToEntity(final ItemsDTO itemsDTO, final Items items) {
        if (itemsDTO == null) {
            return;
        }
        ItemId itemId = new ItemId(itemsDTO.getOrderNo(), itemsDTO.getItemNo());
        items.setId(itemId);
        items.setPartNo(itemsDTO.getPartNo());
        items.setProfilWidth(itemsDTO.getProfilWidth());
        items.setEstimationWidth(itemsDTO.getEstimationWidth());
        items.setEstimationHeight(itemsDTO.getEstimationHeight());
        items.setWidth(itemsDTO.getWidth());
        items.setHeight(itemsDTO.getHeight());
        items.setQty(itemsDTO.getQty());
        items.setQuantity(itemsDTO.getQuantity());
        items.setSellPrice(itemsDTO.getSellPrice());
        items.setResellerPrice(itemsDTO.getResellerPrice());
        items.setDiscount(itemsDTO.getDiscount());
        items.setOnHand(itemsDTO.getOnHand());
        items.setCost(itemsDTO.getCost());
        items.setSum(itemsDTO.getSum());
    }

}
