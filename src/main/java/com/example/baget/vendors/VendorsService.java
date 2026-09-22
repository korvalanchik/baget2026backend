package com.example.baget.vendors;

import com.example.baget.util.NotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VendorsService {

    private final VendorsRepository vendorsRepository;

    public VendorsService(final VendorsRepository vendorsRepository) {
        this.vendorsRepository = vendorsRepository;
    }

    public List<VendorsDTO> findAll() {
        final List<Vendors> vendorses = vendorsRepository.findAll(Sort.by("vendorNo"));
        return vendorses.stream()
                .map(vendors -> mapToDTO(vendors, new VendorsDTO()))
                .toList();
    }

    public VendorsDTO get(final Long vendorNo) {
        return vendorsRepository.findById(vendorNo)
                .map(vendors -> mapToDTO(vendors, new VendorsDTO()))
                .orElseThrow(NotFoundException::new);
    }

    public Long create(final VendorsDTO vendorsDTO) {
        final Vendors vendors = new Vendors();
        mapToEntity(vendorsDTO, vendors);
        return vendorsRepository.save(vendors).getVendorNo();
    }

    public void update(final Long vendorNo, final VendorsDTO vendorsDTO) {
        final Vendors vendors = vendorsRepository.findById(vendorNo)
                .orElseThrow(NotFoundException::new);
        mapToEntity(vendorsDTO, vendors);
        vendorsRepository.save(vendors);
    }

    public void delete(final Long vendorNo) {
        vendorsRepository.deleteById(vendorNo);
    }

    private VendorsDTO mapToDTO(final Vendors vendors, final VendorsDTO vendorsDTO) {
        vendorsDTO.setVendorNo(vendors.getVendorNo());
        vendorsDTO.setVendorName(vendors.getVendorName());
        vendorsDTO.setAddress1(vendors.getAddress1());
        vendorsDTO.setAddress2(vendors.getAddress2());
        vendorsDTO.setCity(vendors.getCity());
        vendorsDTO.setState(vendors.getState());
        vendorsDTO.setZip(vendors.getZip());
        vendorsDTO.setCountry(vendors.getCountry());
        vendorsDTO.setPhone(vendors.getPhone());
        vendorsDTO.setFax(vendors.getFax());
        vendorsDTO.setPreferred(vendors.getPreferred());
        return vendorsDTO;
    }

    private Vendors mapToEntity(final VendorsDTO vendorsDTO, final Vendors vendors) {
        vendors.setVendorName(vendorsDTO.getVendorName());
        vendors.setAddress1(vendorsDTO.getAddress1());
        vendors.setAddress2(vendorsDTO.getAddress2());
        vendors.setCity(vendorsDTO.getCity());
        vendors.setState(vendorsDTO.getState());
        vendors.setZip(vendorsDTO.getZip());
        vendors.setCountry(vendorsDTO.getCountry());
        vendors.setPhone(vendorsDTO.getPhone());
        vendors.setFax(vendorsDTO.getFax());
        vendors.setPreferred(vendorsDTO.getPreferred());
        return vendors;
    }

}
