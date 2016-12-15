package org.motechproject.nms.kilkari.service.impl;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.joda.time.format.DateTimeParser;
import org.motechproject.nms.kilkari.domain.MctsChild;
import org.motechproject.nms.kilkari.domain.MctsMother;
import org.motechproject.nms.kilkari.exception.InvalidReferenceDateException;
import org.motechproject.nms.kilkari.exception.InvalidRegistrationIdException;
import org.motechproject.nms.kilkari.repository.MctsChildDataService;
import org.motechproject.nms.kilkari.repository.MctsMotherDataService;
import org.motechproject.nms.kilkari.service.MctsBeneficiaryValueProcessor;
import org.motechproject.nms.kilkari.utils.KilkariConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("mctsBeneficiaryValueProcessor")
public class MctsBeneficiaryValueProcessorImpl implements MctsBeneficiaryValueProcessor {

    @Autowired
    private MctsMotherDataService mctsMotherDataService;

    @Autowired
    private MctsChildDataService mctsChildDataService;

    @Override
    public MctsMother getOrCreateMotherInstance(String value) {
        MctsMother mother = mctsMotherDataService.findByBeneficiaryId(value);
        if (mother == null) {
            mother = new MctsMother(value);
        }
        return mother;
    }

    @Override
    public MctsMother getMotherInstanceByBeneficiaryId(String value) {
        if (value == null) {
            return null;
        }
        return mctsMotherDataService.findByBeneficiaryId(value);
    }

    @Override
    public Boolean getAbortionDataFromString(String value) {
        String trimmedValue = value.trim();
        return "Spontaneous".equals(trimmedValue) || "MTP<12 Weeks".equals(trimmedValue) ||
                "MTP>12 Weeks".equals(trimmedValue); // "None" or blank indicates no abortion/miscarriage
    }

    @Override
    public Boolean getStillBirthFromString(String value) {
        return "0".equals(value.trim()); // This value indicates the number of live births that resulted from this pregnancy.
        // 0 implies stillbirth, other values (including blank) do not.
    }

    @Override
    public Boolean getDeathFromString(String value) {
        return "9".equals(value.trim()); // 9 indicates beneficiary death; other values do not
    }

    @Override
    public MctsChild getChildInstanceByString(String value) {
        MctsChild child = mctsChildDataService.findByBeneficiaryId(value);
        if (child == null) {
            child = new MctsChild(value);
        }
        return child;
    }

    @Override
    public DateTime getDateByString(String value) {
        if (value == null) {
            return null;
        }

        DateTime referenceDate;

        try {
            DateTimeParser[] parsers = {
                    DateTimeFormat.forPattern("dd-MM-yyyy").getParser(),
                    DateTimeFormat.forPattern("dd/MM/yyyy").getParser()};
            DateTimeFormatter formatter = new DateTimeFormatterBuilder().append(null, parsers).toFormatter();

            referenceDate = formatter.parseDateTime(value);

        } catch (IllegalArgumentException e) {
            throw new InvalidReferenceDateException(String.format("Reference date %s is invalid", value), e);
        }

        return referenceDate;
    }

    @Override
    public Long getMsisdnByString(String value) {
        if (value.length() < KilkariConstants.MSISDN_LENGTH) {
            throw new NumberFormatException("Beneficiary MSISDN too short, must be at least 10 digits");
        }
        String msisdn = value.substring(value.length() - KilkariConstants.MSISDN_LENGTH);

        return Long.parseLong(msisdn);
    }

    @Override
    public Long getCaseNoByString(String value) {

        if (value == null) {
            return null;
        }
        return Long.parseLong(value);
    }

    @Override
    public MctsMother getOrCreateRchMotherInstance(String rchId, String mctsId) {

        MctsMother motherByMctsId = null;
        MctsMother motherByRchId = mctsMotherDataService.findByRchId(rchId);
        if (mctsId != null) {
            motherByMctsId = mctsMotherDataService.findByBeneficiaryId(mctsId);
        }

        if (motherByRchId == null) {
            if (motherByMctsId == null) {
                motherByRchId = new MctsMother(rchId, mctsId);
                return motherByRchId;
            } else {
                motherByMctsId.setRchId(rchId);   // Ajai -this will update rchId if its not null..Is that fine?
                return motherByMctsId;
            }
        } else {
            if (motherByMctsId == null) {
                if (mctsId != null) {
                    motherByRchId.setBeneficiaryId(mctsId);
                }
                return motherByRchId;
            } else {
                if ((motherByRchId.getBeneficiaryId() != motherByMctsId.getBeneficiaryId()) && (motherByRchId.getRchId() != motherByMctsId.getRchId())) {
                    throw new InvalidRegistrationIdException("Invalid record");
                } else {
                    return motherByRchId;
                }
            }
        }
    }

    @Override
    public MctsChild getOrCreateRchChildInstance(String rchId, String mctsId) {

        MctsChild childByMctsId = null;
        MctsChild childByRchId = mctsChildDataService.findByRchId(rchId);
        if (mctsId != null) {
            childByMctsId = mctsChildDataService.findByBeneficiaryId(mctsId);
        }

        if (childByRchId == null) {
            if (childByMctsId == null) {
                childByRchId = new MctsChild(rchId, mctsId);
                return childByRchId;
            } else {
                childByMctsId.setRchId(rchId);   // Ajai -this will update rchId if its not null..Is that fine?
                return childByMctsId;
            }
        } else {
            if (childByMctsId == null) {
                if (mctsId != null) {
                    childByRchId.setBeneficiaryId(mctsId);
                }
                return childByRchId;
            } else {
                if ((childByRchId.getBeneficiaryId() != childByMctsId.getBeneficiaryId()) && (childByRchId.getRchId() != childByMctsId.getRchId())) {
                    throw new InvalidRegistrationIdException("Invalid record");
                } else {
                    return childByRchId;
                }
            }
        }
    }
}