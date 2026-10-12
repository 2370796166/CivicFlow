package com.civicflow.queue.controller;
import com.civicflow.queue.config.RequestIdFilter;
import com.civicflow.common.api.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.*;
import java.time.Instant;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController @Validated @RequestMapping("/api/v1/admin/audit/queue") @PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController{
    private final NamedParameterJdbcTemplate jdbc;public AdminAuditController(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Audit(String id,String actorId,String targetId,String operation,String category,String requestId,Instant occurredAt){}
    @GetMapping @Transactional(readOnly=true) ApiResponse<PageResponse<Audit>> list(
        @RequestParam(required=false) @Size(max=64) String requestId,
        @RequestParam(defaultValue="1") @Min(1) @Max(10000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,
        HttpServletRequest request){
        var params=new MapSqlParameterSource();String where="";
        if(requestId!=null&&!requestId.isBlank()){where=" WHERE request_id=:request";params.addValue("request",requestId);}
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM queue_operation_log"+where,params,Long.class);
        params.addValue("size",size).addValue("offset",(long)(page-1)*size);
        var rows=jdbc.query("SELECT id,actor_id,appointment_id,operation,actor_type,request_id,occurred_at FROM queue_operation_log"+where+" ORDER BY occurred_at DESC,id DESC LIMIT :size OFFSET :offset",
            params,(r,n)->new Audit(r.getString("id"),r.getString("actor_id"),r.getString("appointment_id"),r.getString("operation"),r.getString("actor_type"),r.getString("request_id"),r.getTimestamp("occurred_at").toInstant()));
        return ApiResponse.success(PageResponse.of(rows,page,size,count),RequestIdFilter.current(request));
    }
}
