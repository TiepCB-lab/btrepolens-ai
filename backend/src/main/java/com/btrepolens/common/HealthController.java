package com.btrepolens.common;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
public class HealthController {
    @GetMapping("/api/v1/health") public ApiResponse<Map<String,String>> health(){return ApiResponse.ok(Map.of("status","UP"),"Application is live");}
}
