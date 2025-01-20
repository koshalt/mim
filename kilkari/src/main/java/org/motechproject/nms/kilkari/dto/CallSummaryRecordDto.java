package org.motechproject.nms.kilkari.dto;

import org.motechproject.mds.annotations.Ignore;
import org.motechproject.nms.kilkari.domain.Subscription;
import org.motechproject.nms.props.domain.FinalCallStatus;
import org.motechproject.nms.props.domain.StatusCode;
import org.motechproject.nms.region.domain.Circle;
import org.motechproject.nms.region.domain.Language;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class CallSummaryRecordDto implements Serializable {

    private static final long serialVersionUID = -8391255985224161089L;
    public static final String EMPTY_STRING = "";


    private String subscriptionId;

    private int statusCode;

    private int finalStatus;

    private String contentFileName;

    private String weekId;

    private String languageCode;

    private String circleName;

    private String targetFileTimeStamp;

    private boolean opt_in_call_eligibility;

    private String opt_in_input;

    private String serviceId;

    public CallSummaryRecordDto() { }

    //CHECKSTYLE:OFF
    public CallSummaryRecordDto(String subscriptionId, int statusCode, int finalStatus, String contentFileName,
                                String weekId, String languageCode, String circleName, String targetFileTimeStamp) {
        this.subscriptionId = subscriptionId;
        this.statusCode = statusCode;
        this.finalStatus = finalStatus;
        this.contentFileName = contentFileName;
        this.weekId = weekId;
        this.languageCode = languageCode;
        this.circleName = circleName;
        this.targetFileTimeStamp = targetFileTimeStamp;
    }

    public CallSummaryRecordDto(String subscriptionId, int statusCode, int finalStatus, String contentFileName,
                                String weekId, String languageCode, String circleName, String targetFileTimeStamp, Boolean opt_in_call_eligibility, String opt_in_input,String serviceId) {
        this.subscriptionId = subscriptionId;
        this.statusCode = statusCode;
        this.finalStatus = finalStatus;
        this.contentFileName = contentFileName;
        this.weekId = weekId;
        this.languageCode = languageCode;
        this.circleName = circleName;
        this.targetFileTimeStamp = targetFileTimeStamp;
        this.opt_in_call_eligibility = opt_in_call_eligibility;
        this.opt_in_input = opt_in_input;
        this.serviceId = serviceId;
    }

    // Helper constructor for ITs
    public CallSummaryRecordDto(Subscription subscription, StatusCode statusCode, FinalCallStatus finalStatus,
                                String contentFileName, String weekId, Language language, Circle circle,
                                String targetFileTimeStamp) {
        this(
                subscription == null ? EMPTY_STRING : subscription.getSubscriptionId(),
                statusCode == null ? -1 : statusCode.getValue(),
                finalStatus == null ? -1 : finalStatus.getValue(),
                contentFileName == null ? EMPTY_STRING : contentFileName,
                weekId == null ? EMPTY_STRING : weekId,
                language == null ? EMPTY_STRING : language.getCode(),
                circle == null ? EMPTY_STRING : circle.getName(),
                targetFileTimeStamp == null ? EMPTY_STRING : targetFileTimeStamp
        );
    }

    //CHECKSTYLE:ON
    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public int getFinalStatus() {
        return finalStatus;
    }

    public boolean isOpt_in_call_eligibility() {return opt_in_call_eligibility;}

    public void setOpt_in_call_eligibility(boolean opt_in_call_eligibility) {this.opt_in_call_eligibility = opt_in_call_eligibility;}

    public String getOpt_in_input() {return opt_in_input;}

    public void setOpt_in_input(String opt_in_input) {this.opt_in_input = opt_in_input;}

    public void setFinalStatus(int finalStatus) {
        this.finalStatus = finalStatus;
    }

    public String getContentFileName() {
        return contentFileName;
    }

    public void setContentFileName(String contentFileName) {
        this.contentFileName = contentFileName;
    }

    public String getWeekId() {
        return weekId;
    }

    public void setWeekId(String weekId) {
        this.weekId = weekId;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public String getCircleName() {
        return circleName;
    }

    public void setCircleName(String circleName) {
        this.circleName = circleName;
    }

    public String getTargetFileTimeStamp() {
        return targetFileTimeStamp;
    }

    public void setTargetFileTimeStamp(String targetFileTimeStamp) {
        this.targetFileTimeStamp = targetFileTimeStamp;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    @Ignore
    public static CallSummaryRecordDto fromParams(Map<String, Object> params) {
        CallSummaryRecordDto csr;
        csr = new CallSummaryRecordDto(
                (String) params.get("subscriptionId"),
                (int) params.get("statusCode"),
                (int) params.get("finalStatus"),
                (String) params.get("contentFileName"),
                (String) params.get("weekId"),
                (String) params.get("languageCode"),
                (String) params.get("circleName"),
                (String) params.get("targetFileTimeStamp"),
                (Boolean) params.get("opt_in_call_eligibility"),
                (String) params.get("opt_in_input"),
                (String) params.get("serviceId")
        );
        return csr;
    }

    @Ignore
    public static Map<String, Object> toParams(CallSummaryRecordDto csr) {
        Map<String, Object> params = new HashMap<>();
        params.put("subscriptionId", csr.subscriptionId);
        params.put("statusCode", csr.statusCode);
        params.put("finalStatus", csr.finalStatus);
        params.put("contentFileName", csr.contentFileName);
        params.put("weekId", csr.weekId);
        params.put("languageCode", csr.languageCode);
        params.put("circleName", csr.circleName);
        params.put("targetFileTimeStamp", csr.targetFileTimeStamp);
        params.put("opt_in_call_eligibility", csr.opt_in_call_eligibility);
        params.put("opt_in_input", csr.opt_in_input);
        params.put("serviceId", csr.serviceId);
        return params;
    }
}
