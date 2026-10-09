package com.his.outpatient.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/** 医生排班号源 */
@Data
public class DoctorSchedule {
    private Long id;
    private Long doctorId;
    private String doctorName;
    private String department;
    private Date visitDate;
    private String timeSlot;
    private Integer totalCount;
    private Integer bookedCount;
    private BigDecimal fee;
    /** OPEN/CLOSED/FULL */
    private String status;
}
