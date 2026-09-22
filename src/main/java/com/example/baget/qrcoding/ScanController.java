package com.example.baget.qrcoding;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scan")
@RequiredArgsConstructor
public class ScanController {

//    private final ScanService scanService;

//    @GetMapping
//    public ResponseEntity<?> scan(@RequestParam String token, Principal principal) {
//        try {
//            return ResponseEntity.ok(scanService.scanOrder(token, principal));
//        } catch (AccessDeniedException e) {
//            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
//        } catch (EntityNotFoundException e) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
//        } catch (JWTVerificationException e) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid or expired token");
//        }
//    }
//
//    @PostMapping("/action")
//    public ResponseEntity<?> changeStatus(@RequestParam String token,
//                                          @RequestParam Integer status,
//                                          Principal principal) {
//        try {
//            scanService.updateOrderStatus(token, status, principal);
//            return ResponseEntity.ok("Status updated");
//        } catch (AccessDeniedException e) {
//            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
//        } catch (EntityNotFoundException e) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
//        } catch (JWTVerificationException e) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid or expired token");
//        }
//    }
}
