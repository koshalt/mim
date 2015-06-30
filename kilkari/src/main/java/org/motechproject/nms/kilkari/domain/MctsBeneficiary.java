package org.motechproject.nms.kilkari.domain;

import org.motechproject.mds.annotations.Entity;
import org.motechproject.mds.annotations.Field;
import org.motechproject.nms.region.domain.District;
import org.motechproject.nms.region.domain.FullLocation;
import org.motechproject.nms.region.domain.HealthBlock;
import org.motechproject.nms.region.domain.HealthFacility;
import org.motechproject.nms.region.domain.HealthSubFacility;
import org.motechproject.nms.region.domain.State;
import org.motechproject.nms.region.domain.Taluka;
import org.motechproject.nms.region.domain.Village;
import org.motechproject.nms.region.domain.validation.ValidFullLocation;

import javax.jdo.annotations.Unique;
import javax.validation.constraints.NotNull;

/**
 * A beneficiary (mother or child) sourced from MCTS.
 */
@ValidFullLocation
@Entity(tableName = "nms_mcts_beneficiaries")
public abstract class MctsBeneficiary implements FullLocation {

    // 18-digit IDs are used for most states but not all, so a strict length constraint cannot be set for this column
    @Field
    @Unique
    @NotNull
    private String beneficiaryId;

    @Field
    private String name;

    @Field
    private State state;

    @Field
    private District district;

    @Field
    private Taluka taluka;

    @Field
    private HealthBlock healthBlock;

    @Field
    private HealthFacility primaryHealthCenter;

    @Field
    private HealthSubFacility healthSubFacility;

    @Field
    private Village village;

    public MctsBeneficiary() {
    }

    public MctsBeneficiary(String beneficiaryId) {
        this(beneficiaryId, null);
    }

    public MctsBeneficiary(String beneficiaryId, String name) {
        this.beneficiaryId = beneficiaryId;
        this.name = name;
    }

    public String getBeneficiaryId() {
        return beneficiaryId;
    }

    public void setBeneficiaryId(String beneficiaryId) {
        this.beneficiaryId = beneficiaryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void setState(State state) {
        this.state = state;
    }

    @Override
    public District getDistrict() {
        return district;
    }

    @Override
    public void setDistrict(District district) {
        this.district = district;
    }

    @Override
    public Taluka getTaluka() {
        return taluka;
    }

    @Override
    public void setTaluka(Taluka taluka) {
        this.taluka = taluka;
    }

    @Override
    public HealthBlock getHealthBlock() {
        return healthBlock;
    }

    @Override
    public void setHealthBlock(HealthBlock healthBlock) {
        this.healthBlock = healthBlock;
    }

    @Override
    public HealthFacility getHealthFacility() {
        return primaryHealthCenter;
    }

    @Override
    public void setHealthFacility(HealthFacility primaryHealthCenter) {
        this.primaryHealthCenter = primaryHealthCenter;
    }

    @Override
    public HealthSubFacility getHealthSubFacility() {
        return healthSubFacility;
    }

    @Override
    public void setHealthSubFacility(HealthSubFacility healthSubFacility) {
        this.healthSubFacility = healthSubFacility;
    }

    @Override
    public Village getVillage() {
        return village;
    }

    @Override
    public void setVillage(Village village) {
        this.village = village;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MctsBeneficiary that = (MctsBeneficiary) o;

        return !(beneficiaryId != null ? !beneficiaryId.equals(that.beneficiaryId) : that.beneficiaryId != null);

    }

    @Override
    public int hashCode() {
        return beneficiaryId != null ? beneficiaryId.hashCode() : 0;
    }
}
