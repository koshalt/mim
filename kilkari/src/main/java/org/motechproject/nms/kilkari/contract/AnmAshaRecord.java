package org.motechproject.nms.kilkari.contract;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.motechproject.nms.kilkari.utils.FlwConstants;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.util.HashMap;
import java.util.Map;

@XmlType
@XmlAccessorType(XmlAccessType.NONE)
public class AnmAshaRecord {

    private Long id;
    private Long stateId;
    private Long districtId;
    private String districtName;
    private String talukaId;
    private String talukaName;
    private Long healthBlockId;
    private String healthBlockName;
    private Long phcId;
    private String phcName;
    private Long subCentreId;
    private String subCentreName;
    private Long villageId;
    private String villageName;
    private String regDate;
    private String name;
    private String contactNo;
    private String sex;
    private String type;
    private String smsReply;
    private Integer aadharNo;
    private String createdOn;
    private String updatedOn;
    private Integer bankId;
    private String branchName;
    private String ifscIdCode;
    private String bankName;
    private String accNo;
    private Boolean isAadharLinked;
    private String verifyDate;
    private String verifierName;
    private Integer verifierId;
    private Boolean callAns;
    private Boolean isPhoneNoCorrect;
    private Integer noCallReason;
    private Integer noPhoneReason;
    private String verifierRemarks;
    private String gfAddress;
    private String husbandName;
    private String gfStatus;

    public Long getId() {
        return id;
    }

    @XmlElement(name = "ID")
    public void setId(Long id) {
        this.id = id;
    }

    public Long getStateId() {
        return stateId;
    }

    @XmlElement(name = "StateID")
    public void setStateId(Long stateID) {
        this.stateId = stateID;
    }

    public Long getHealthBlockId() {
        return healthBlockId;
    }

    public String getTalukaName() {
        return talukaName;
    }

    @XmlElement(name = "Taluka_Name")
    public void setTalukaName(String talukaName) {
        this.talukaName = talukaName;
    }

    @XmlElement(name = "Village_Name")
    public void setVillageName(String villageName) {
        this.villageName = villageName;
    }

    public String getRegDate() {
        return regDate;
    }

    public String getSubCentreName() {
        return subCentreName;
    }

    @XmlElement(name = "SubCentre_Name")
    public void setSubCentreName(String subCentreName) {
        this.subCentreName = subCentreName;
    }

    public Long getVillageId() {
        return villageId;
    }

    @XmlElement(name = "Village_ID")
    public void setVillageId(Long villageId) {
        this.villageId = villageId;
    }

    public String getVillageName() {
        return villageName;
    }

    public Long getSubCentreId() {
        return subCentreId;
    }

    @XmlElement(name = "SubCentre_ID")
    public void setSubCentreId(Long subCentreId) {
        this.subCentreId = subCentreId;
    }

    @XmlElement(name = "District_ID")
    public void setDistrictId(Long districtId) {
        this.districtId = districtId;
    }

    public String getDistrictName() {
        return districtName;
    }

    @XmlElement(name = "District_Name")
    public void setDistrictName(String districtName) {
        this.districtName = districtName;
    }

    public String getTalukaId() {
        return talukaId;
    }

    @XmlElement(name = "Taluka_ID")
    public void setTalukaId(String talukaId) {
        this.talukaId = talukaId;
    }

    @XmlElement(name = "Reg_Date")
    public void setRegDate(String regDate) {
        this.regDate = regDate;
    }

    public String getName() {
        return name;
    }

    @XmlElement(name = "Name")
    public void setName(String name) {
        this.name = name;
    }

    public String getContactNo() {
        return contactNo;
    }

    @XmlElement(name = "Contact_No")
    public void setContactNo(String contactNo) {
        this.contactNo = contactNo;
    }

    public String getSex() {
        return sex;
    }

    @XmlElement(name = "Sex")
    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getType() {
        return type;
    }

    @XmlElement(name = "Type")
    public void setType(String type) {
        this.type = type;
    }

    public String getSmsReply() {
        return smsReply;
    }

    @XmlElement(name = "SMS_Reply")
    public void setSmsReply(String smsReply) {
        this.smsReply = smsReply;
    }

    public Integer getAadharNo() {
        return aadharNo;
    }

    @XmlElement(name = "Aadhar_No")
    public void setAadharNo(Integer aadharNo) {
        this.aadharNo = aadharNo;
    }

    public String getCreatedOn() {
        return createdOn;
    }

    @XmlElement(name = "Created_On")
    public void setCreatedOn(String createdOn) {
        this.createdOn = createdOn;
    }

    public String getPhcName() {
        return phcName;
    }

    @XmlElement(name = "PHC_Name")
    public void setPhcName(String phcName) {
        this.phcName = phcName;
    }

    public Long getDistrictId() {
        return districtId;
    }

    public String getUpdatedOn() {
        return updatedOn;
    }

    @XmlElement(name = "Updated_On")
    public void setUpdatedOn(String updatedOn) {
        this.updatedOn = updatedOn;
    }

    public Integer getBankId() {
        return bankId;
    }

    @XmlElement(name = "HealthBlock_ID")
    public void setHealthBlockId(Long healthBlockId) {
        this.healthBlockId = healthBlockId;
    }

    public String getHealthBlockName() {
        return healthBlockName;
    }

    @XmlElement(name = "HealthBlock_Name")
    public void setHealthBlockName(String healthBlockName) {
        this.healthBlockName = healthBlockName;
    }

    public Long getPhcId() {
        return phcId;
    }

    @XmlElement(name = "PHC_ID")
    public void setPhcId(Long phcId) {
        this.phcId = phcId;
    }

    @XmlElement(name = "Bank_ID")
    public void setBankId(Integer bankId) {
        this.bankId = bankId;
    }

    public String getBranchName() {
        return branchName;
    }

    @XmlElement(name = "Branch_Name")
    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getIfscIdCode() {
        return ifscIdCode;
    }

    @XmlElement(name = "IFSC_ID_Code")
    public void setIfscIdCode(String ifscIdCode) {
        this.ifscIdCode = ifscIdCode;
    }

    public String getBankName() {
        return bankName;
    }

    @XmlElement(name = "Bank_Name")
    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getAccNo() {
        return accNo;
    }

    @XmlElement(name = "Acc_No")
    public void setAccNo(String accNo) {
        this.accNo = accNo;
    }

    public Boolean getIsAadharLinked() {
        return isAadharLinked;
    }

    @XmlElement(name = "Is_Aadhar_linked")
    public void setIsAadharLinked(Boolean isAadharLinked) {
        this.isAadharLinked = isAadharLinked;
    }

    public String getVerifyDate() {
        return verifyDate;
    }

    @XmlElement(name = "Verify_Date")
    public void setVerifyDate(String verifyDate) {
        this.verifyDate = verifyDate;
    }

    public String getVerifierName() {
        return verifierName;
    }

    @XmlElement(name = "Verifier_Name")
    public void setVerifierName(String verifierName) {
        this.verifierName = verifierName;
    }

    public Integer getVerifierId() {
        return verifierId;
    }

    @XmlElement(name = "VerifierID")
    public void setVerifierId(Integer verifierId) {
        this.verifierId = verifierId;
    }

    public Boolean getCallAns() {
        return callAns;
    }

    @XmlElement(name = "Call_Ans")
    public void setCallAns(Boolean callAns) {
        this.callAns = callAns;
    }

    public Boolean getIsPhoneNoCorrect() {
        return isPhoneNoCorrect;
    }

    @XmlElement(name = "IsPhoneNoCorrect")
    public void setIsPhoneNoCorrect(Boolean isPhoneNoCorrect) {
        this.isPhoneNoCorrect = isPhoneNoCorrect;
    }

    public Integer getNoCallReason() {
        return noCallReason;
    }

    @XmlElement(name = "NoCall_Reason")
    public void setNoCallReason(Integer noCallReason) {
        this.noCallReason = noCallReason;
    }

    public Integer getNoPhoneReason() {
        return noPhoneReason;
    }

    @XmlElement(name = "NoPhone_Reason")
    public void setNoPhoneReason(Integer noPhoneReason) {
        this.noPhoneReason = noPhoneReason;
    }

    public String getVerifierRemarks() {
        return verifierRemarks;
    }

    @XmlElement(name = "Verifier_Remarks")
    public void setVerifierRemarks(String verifierRemarks) {
        this.verifierRemarks = verifierRemarks;
    }

    public String getGfAddress() {
        return gfAddress;
    }

    @XmlElement(name = "GF_Address")
    public void setGfAddress(String gfAddress) {
        this.gfAddress = gfAddress;
    }

    public String getHusbandName() {
        return husbandName;
    }

    @XmlElement(name = "Husband_Name")
    public void setHusbandName(String husbandName) {
        this.husbandName = husbandName;
    }

    public String getGfStatus() { return gfStatus; }

    @XmlElement(name = "GF_Status")
    public void setGfStatus(String gfStatus) { this.gfStatus = gfStatus; }

    public Map<String, Object> toFlwRecordMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(FlwConstants.ID, getId() == null ? null : getId().toString());
        map.put(FlwConstants.CONTACT_NO, getContactNo() == null ? null : Long.parseLong(getContactNo()));
        map.put(FlwConstants.PHC_NAME, getPhcName());
        map.put(FlwConstants.SUB_CENTRE_ID, getSubCentreId());
        map.put(FlwConstants.SUB_CENTRE_NAME, getSubCentreName());
        map.put(FlwConstants.CENSUS_VILLAGE_ID, getVillageId());
        map.put(FlwConstants.VILLAGE_NAME, getVillageName());
        map.put(FlwConstants.NAME, getName());
        map.put(FlwConstants.DISTRICT_ID, getDistrictId());
        map.put(FlwConstants.DISTRICT_NAME, getDistrictName());
        map.put(FlwConstants.TALUKA_ID, getTalukaId());
        map.put(FlwConstants.TALUKA_NAME, getTalukaName());
        map.put(FlwConstants.HEALTH_BLOCK_ID, getHealthBlockId());
        map.put(FlwConstants.HEALTH_BLOCK_NAME, getHealthBlockName());
        map.put(FlwConstants.PHC_ID, getPhcId());
        map.put(FlwConstants.TYPE, getType());
        map.put(FlwConstants.GF_STATUS, getGfStatus());
        map.put(FlwConstants.UPDATED_ON, "".equals(getUpdatedOn()) ? null : LocalDate.parse(getUpdatedOn(), DateTimeFormat.forPattern("dd-MM-yyyy")));
        return map;
    }
}
