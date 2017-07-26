package org.motechproject.nms.kilkari.service.impl;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.motechproject.metrics.service.Timer;
import org.motechproject.nms.csv.exception.CsvImportDataException;
import org.motechproject.nms.csv.utils.ConstraintViolationUtils;
import org.motechproject.nms.csv.utils.CsvImporterBuilder;
import org.motechproject.nms.csv.utils.CsvMapImporter;
import org.motechproject.nms.csv.utils.GetInstanceByString;
import org.motechproject.nms.csv.utils.GetString;
import org.motechproject.nms.kilkari.domain.DeactivatedBeneficiary;
import org.motechproject.nms.kilkari.domain.DeactivationReason;
import org.motechproject.nms.kilkari.domain.MctsChild;
import org.motechproject.nms.kilkari.domain.MctsMother;
import org.motechproject.nms.kilkari.domain.Subscription;
import org.motechproject.nms.kilkari.domain.SubscriptionPackType;
import org.motechproject.nms.kilkari.domain.Subscriber;
import org.motechproject.nms.kilkari.domain.SubscriptionOrigin;
import org.motechproject.nms.kilkari.domain.SubscriptionError;
import org.motechproject.nms.kilkari.domain.SubscriptionPack;
import org.motechproject.nms.kilkari.domain.SubscriptionRejectionReason;
import org.motechproject.nms.kilkari.domain.SubscriptionStatus;
import org.motechproject.nms.kilkari.exception.MultipleSubscriberException;
import org.motechproject.nms.kilkari.repository.DeactivatedBeneficiaryDataService;
import org.motechproject.nms.kilkari.repository.MctsMotherDataService;
import org.motechproject.nms.kilkari.repository.SubscriptionErrorDataService;
import org.motechproject.nms.kilkari.service.MctsBeneficiaryImportService;
import org.motechproject.nms.kilkari.service.MctsBeneficiaryValueProcessor;
import org.motechproject.nms.kilkari.service.SubscriberService;
import org.motechproject.nms.kilkari.service.SubscriptionService;
import org.motechproject.nms.kilkari.utils.KilkariConstants;
import org.motechproject.nms.kilkari.utils.MctsBeneficiaryUtils;
import org.motechproject.nms.kilkari.domain.RejectionReasons;
import org.motechproject.nms.region.exception.InvalidLocationException;
import org.motechproject.nms.region.service.LocationService;
import org.motechproject.nms.kilkari.service.ActionFinderService;
import org.motechproject.nms.rejectionhandler.service.ChildRejectionService;
import org.motechproject.nms.rejectionhandler.service.MotherRejectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.prefs.CsvPreference;

import javax.validation.ConstraintViolationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.motechproject.nms.kilkari.utils.RejectedObjectConverter.childRejectionMcts;
import static org.motechproject.nms.kilkari.utils.RejectedObjectConverter.motherRejectionMcts;
import static org.motechproject.nms.kilkari.utils.RejectedObjectConverter.convertMapToChild;
import static org.motechproject.nms.kilkari.utils.RejectedObjectConverter.convertMapToMother;
import static org.motechproject.nms.kilkari.utils.RejectedObjectConverter.childRejectionRch;
import static org.motechproject.nms.kilkari.utils.RejectedObjectConverter.motherRejectionRch;
import static org.motechproject.nms.kilkari.utils.RejectedObjectConverter.convertMapToRchChild;
import static org.motechproject.nms.kilkari.utils.RejectedObjectConverter.convertMapToRchMother;

/**
 * Implementation of the {@link MctsBeneficiaryImportService} interface.
 */
@Service("mctsBeneficiaryImportService")
public class MctsBeneficiaryImportServiceImpl implements MctsBeneficiaryImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MctsBeneficiaryImportServiceImpl.class);

    private SubscriptionService subscriptionService;
    private SubscriptionErrorDataService subscriptionErrorDataService;
    private LocationService locationService;
    private SubscriberService subscriberService;
    private MctsBeneficiaryValueProcessor mctsBeneficiaryValueProcessor;
    private SubscriptionPack pregnancyPack;
    private SubscriptionPack childPack;
    private MctsMotherDataService mctsMotherDataService;
    private DeactivatedBeneficiaryDataService deactivatedBeneficiaryDataService;

    @Autowired
    public MctsBeneficiaryImportServiceImpl(SubscriptionService subscriptionService,
                                            SubscriptionErrorDataService subscriptionErrorDataService,
                                            LocationService locationService, SubscriberService subscriberService,
                                            MctsBeneficiaryValueProcessor mctsBeneficiaryValueProcessor,
                                            MctsMotherDataService mctsMotherDataService,
                                            DeactivatedBeneficiaryDataService deactivatedBeneficiaryDataService) {
        this.subscriptionService = subscriptionService;
        this.subscriptionErrorDataService = subscriptionErrorDataService;
        this.locationService = locationService;
        this.subscriberService = subscriberService;
        this.mctsBeneficiaryValueProcessor = mctsBeneficiaryValueProcessor;
        this.mctsMotherDataService = mctsMotherDataService;
        this.deactivatedBeneficiaryDataService = deactivatedBeneficiaryDataService;
    }

    @Autowired
    private MotherRejectionService motherRejectionService;

    @Autowired
    private ChildRejectionService childRejectionService;

    @Autowired
    private ActionFinderService actionFinderService;

    /**
     * Expected file format:
     * - any number of empty lines
     * - header lines in the following format:  State Name : ACTUAL STATE_ID NAME
     * - one empty line
     * - CSV data (tab-separated)
     */
    @Override
    @Transactional
    public int importMotherData(Reader reader, SubscriptionOrigin importOrigin) throws IOException {
        pregnancyPack = subscriptionService.getSubscriptionPack(SubscriptionPackType.PREGNANCY);
        int count = 0;
        /**
         * Count of all the records rejected for unknown exceptions. So, doesn't include the ones saved in nms_subscription_errors.
         * This is used just for debugging purpose.
         */
        int rejectedWithException = 0;

        BufferedReader bufferedReader = new BufferedReader(reader);
        Map<String, CellProcessor> cellProcessorMapper;
        String id;
        String contactNumber;

        if (importOrigin.equals(SubscriptionOrigin.MCTS_IMPORT)) {
            cellProcessorMapper = this.getMotherProcessorMapping();
            id = KilkariConstants.BENEFICIARY_ID;
            contactNumber = KilkariConstants.MSISDN;
        } else {
            cellProcessorMapper = this.getRchMotherProcessorMapping();
            id = KilkariConstants.RCH_ID;
            contactNumber = KilkariConstants.MOBILE_NO;
        }

        CsvMapImporter csvImporter = new CsvImporterBuilder()
                .setProcessorMapping(cellProcessorMapper)
                .setPreferences(CsvPreference.TAB_PREFERENCE)
                .createAndOpen(bufferedReader);

        try {
            Map<String, Object> record;
            Timer timer = new Timer("mom", "moms");
            while (null != (record = csvImporter.read())) {
                LOGGER.debug("Started import for msisdn {} beneficiary_id {}", record.get(contactNumber), record.get(id));
                try {
                    importMotherRecord(record, importOrigin);
                    count++;
                    if (count % KilkariConstants.PROGRESS_INTERVAL == 0) {
                        LOGGER.debug(KilkariConstants.IMPORTED, timer.frequency(count));
                    }
                } catch (RuntimeException e) {
                    LOGGER.error("Error at msisdn {} beneficiary_id {}", record.get(contactNumber), record.get(id), e);
                    rejectedWithException++;
                }
            }

            LOGGER.debug(KilkariConstants.IMPORTED, timer.frequency(count));
            LOGGER.debug(KilkariConstants.REJECTED, timer.frequency(rejectedWithException));

        } catch (ConstraintViolationException e) {
            throw new CsvImportDataException(String.format("Mother import error, constraints violated: %s",
                    ConstraintViolationUtils.toString(e.getConstraintViolations())), e);
        }

        return count;
    }

    @Transactional
    public int importChildData(Reader reader, SubscriptionOrigin importOrigin) throws IOException {
        childPack = subscriptionService.getSubscriptionPack(SubscriptionPackType.CHILD);
        int count = 0;
        /**
         * Count of all the records rejected for unknown exceptions. So, doesn't include the ones saved in nms_subscription_errors.
         * This is used just for debugging purpose.
         */
        int rejectedWithException = 0;

        BufferedReader bufferedReader = new BufferedReader(reader);
        Map<String, CellProcessor> cellProcessorMapper;
        String id;
        String contactNumber;

        if (importOrigin.equals(SubscriptionOrigin.MCTS_IMPORT)) {
            cellProcessorMapper = this.getChildProcessorMapping();
            id = KilkariConstants.BENEFICIARY_ID;
            contactNumber = KilkariConstants.MSISDN;
        } else {
            cellProcessorMapper = this.getRchChildProcessorMapping();
            id = KilkariConstants.RCH_ID;
            contactNumber = KilkariConstants.MOBILE_NO;
        }

        CsvMapImporter csvImporter = new CsvImporterBuilder()
                .setProcessorMapping(cellProcessorMapper)
                .setPreferences(CsvPreference.TAB_PREFERENCE)
                .createAndOpen(bufferedReader);

        try {
            Map<String, Object> record;
            Timer timer = new Timer("kid", "kids");
            while (null != (record = csvImporter.read())) {
                LOGGER.debug("Started child import for msisdn {} beneficiary_id {}", record.get(contactNumber), record.get(id));
                try {
                    importChildRecord(record, importOrigin);
                    count++;
                    if (count % KilkariConstants.PROGRESS_INTERVAL == 0) {
                        LOGGER.debug(KilkariConstants.IMPORTED, timer.frequency(count));
                    }
                } catch (RuntimeException e) {
                    LOGGER.error("Error while importing child at msisdn {} beneficiary_id {}", record.get(contactNumber), record.get(id), e);
                    rejectedWithException++;
                }
            }

            LOGGER.debug(KilkariConstants.IMPORTED, timer.frequency(count));
            LOGGER.debug(KilkariConstants.REJECTED, timer.frequency(rejectedWithException));

        } catch (ConstraintViolationException e) {
            throw new CsvImportDataException(String.format("Child import error, constraints violated: %s",
                    ConstraintViolationUtils.toString(e.getConstraintViolations())), e);
        }

        return count;
    }

    @Override // NO CHECKSTYLE Cyclomatic Complexity
    @Transactional
    public boolean importMotherRecord(Map<String, Object> record, SubscriptionOrigin importOrigin) { //NOPMD NcssMethodCount
        if (pregnancyPack == null) {
            pregnancyPack = subscriptionService.getSubscriptionPack(SubscriptionPackType.PREGNANCY);
        }
        MctsMother mother;
        Long msisdn;
        Boolean abortion;
        Boolean stillBirth;
        LocalDate lastUpdatedDateNic;
        String beneficiaryId;
        String action = "";
        Boolean flagForMcts = true;
        if (importOrigin.equals(SubscriptionOrigin.MCTS_IMPORT)) {
            action = actionFinderService.motherActionFinder(convertMapToMother(record));
            beneficiaryId = (String) record.get(KilkariConstants.BENEFICIARY_ID);
            mother = mctsBeneficiaryValueProcessor.getOrCreateMotherInstance(beneficiaryId);
            msisdn = (Long) record.get(KilkariConstants.MSISDN);
            abortion = (Boolean) record.get(KilkariConstants.ABORTION);
            stillBirth = (Boolean) record.get(KilkariConstants.STILLBIRTH);
            lastUpdatedDateNic = (LocalDate) record.get(KilkariConstants.LAST_UPDATE_DATE);
        } else {
            flagForMcts = false;
            action = actionFinderService.rchMotherActionFinder((convertMapToRchMother(record)));
            beneficiaryId = (String) record.get(KilkariConstants.RCH_ID);
            String mctsId = (String) record.get(KilkariConstants.MCTS_ID);
            mother = mctsBeneficiaryValueProcessor.getOrCreateRchMotherInstance(beneficiaryId, mctsId);
            msisdn = (Long) record.get(KilkariConstants.MOBILE_NO);
            abortion = (Boolean) record.get(KilkariConstants.ABORTION_TYPE);
            stillBirth = (Boolean) record.get(KilkariConstants.DELIVERY_OUTCOMES);
            lastUpdatedDateNic = (LocalDate) record.get(KilkariConstants.EXECUTION_DATE);
        }

        String name = (String) record.get(KilkariConstants.BENEFICIARY_NAME);
        DateTime lmp = (DateTime) record.get(KilkariConstants.LMP);
        DateTime motherDOB = (DateTime) record.get(KilkariConstants.MOTHER_DOB);
        Boolean death = (Boolean) record.get(KilkariConstants.DEATH);

        // validate msisdn
        if (!validateMsisdn(msisdn, SubscriptionPackType.PREGNANCY, beneficiaryId, importOrigin)) {
            if (flagForMcts) {
                motherRejectionService.createOrUpdateMother(motherRejectionMcts(convertMapToMother(record), false, RejectionReasons.MSISDN_ALREADY_IN_USE.toString(), action));
            } else {
                motherRejectionService.createOrUpdateMother(motherRejectionRch(convertMapToRchMother(record), false, RejectionReasons.MSISDN_ALREADY_IN_USE.toString(), action));
            }
            return false;
        }

        // validate lmp date. We do not sanitize for lmp in the future to be in sync with MCTS data
        // NOTE: getId is a way to see if this is a new user. We only accept new users if they
        // have 12 weeks left in the pack. For existing users, their lmp could be updated to
        // an earlier date if it's an complete mother record(i.e not created through child import)
        if ((mother.getId() == null || (mother.getId() != null && mother.getLastMenstrualPeriod() == null)) && !validateReferenceDate(lmp, SubscriptionPackType.PREGNANCY, msisdn, beneficiaryId, importOrigin)) {
            if (flagForMcts) {
                motherRejectionService.createOrUpdateMother(motherRejectionMcts(convertMapToMother(record), false, RejectionReasons.INVALID_LMP_DATE.toString(), action));
            } else {
                motherRejectionService.createOrUpdateMother(motherRejectionRch(convertMapToRchMother(record), false, RejectionReasons.INVALID_LMP_DATE.toString(), action));
            }
            return false;
        }

        // validate and set location
        try {
            MctsBeneficiaryUtils.setLocationFields(locationService.getLocations(record), mother);
        } catch (InvalidLocationException le) {
            LOGGER.error(le.toString());
            subscriptionErrorDataService.create(new SubscriptionError(msisdn, beneficiaryId, SubscriptionRejectionReason.INVALID_LOCATION,
                    SubscriptionPackType.PREGNANCY, le.getMessage(), importOrigin));
            if (flagForMcts) {
                motherRejectionService.createOrUpdateMother(motherRejectionMcts(convertMapToMother(record), false, RejectionReasons.INVALID_LOCATION.toString(), action));
            } else {
                motherRejectionService.createOrUpdateMother(motherRejectionRch(convertMapToRchMother(record), false, RejectionReasons.INVALID_LOCATION.toString(), action));
            }
            return false;
        }

        //validate if it's an updated record compared to one from database
        if (mother.getUpdatedDateNic() != null && (lastUpdatedDateNic == null || mother.getUpdatedDateNic().isAfter(lastUpdatedDateNic))) {
            subscriptionErrorDataService.create(new SubscriptionError(msisdn, beneficiaryId,
                    SubscriptionRejectionReason.BENEFICIARY_ALREADY_SUBSCRIBED, SubscriptionPackType.PREGNANCY, "Updated Record exits", importOrigin));
            if (flagForMcts) {
                motherRejectionService.createOrUpdateMother(motherRejectionMcts(convertMapToMother(record), false, RejectionReasons.RECORD_ALREADY_EXISTS.toString(), action));
            } else {
                motherRejectionService.createOrUpdateMother(motherRejectionRch(convertMapToRchMother(record), false, RejectionReasons.RECORD_ALREADY_EXISTS.toString(), action));
            }
            return false;
        }

        List<DeactivatedBeneficiary> deactivatedUsers = deactivatedBeneficiaryDataService.findByExternalId(beneficiaryId);
        if (deactivatedUsers != null && deactivatedUsers.size() > 0) {
            for (DeactivatedBeneficiary deactivatedUser : deactivatedUsers) {
                if (deactivatedUser.getOrigin() == importOrigin) {
                    String message = deactivatedUser.isCompletedSubscription() ? "Subscription completed" : "User deactivated";
                    if (message.length() > 2) {
                        subscriptionErrorDataService.create(new SubscriptionError(msisdn, beneficiaryId,
                                SubscriptionRejectionReason.BENEFICIARY_ALREADY_SUBSCRIBED, SubscriptionPackType.PREGNANCY, message, importOrigin));
                        if (flagForMcts) {
                            motherRejectionService.createOrUpdateMother(motherRejectionMcts(convertMapToMother(record), false, RejectionReasons.RECORD_ALREADY_EXISTS.toString(), action));
                        } else {
                            motherRejectionService.createOrUpdateMother(motherRejectionRch(convertMapToRchMother(record), false, RejectionReasons.RECORD_ALREADY_EXISTS.toString(), action));
                        }
                        return false;
                    }
                }
            }
        }

        mother.setName(name);
        mother.setDateOfBirth(motherDOB);
        mother.setUpdatedDateNic(lastUpdatedDateNic);

        Subscription subscription;
        if (importOrigin.equals(SubscriptionOrigin.MCTS_IMPORT)) {
            //validate if an ACTIVE child is already present for the mother. If yes, ignore the update
            if (childAlreadyPresent(beneficiaryId)) {
                subscriptionErrorDataService.create(new SubscriptionError(msisdn, beneficiaryId,
                        SubscriptionRejectionReason.ACTIVE_CHILD_PRESENT, SubscriptionPackType.PREGNANCY, "Active child is present for this mother.", importOrigin));
                if (flagForMcts) {
                    motherRejectionService.createOrUpdateMother(motherRejectionMcts(convertMapToMother(record), false, RejectionReasons.ACTIVE_CHILD_PRESENT.toString(), action));
                } else {
                    motherRejectionService.createOrUpdateMother(motherRejectionRch(convertMapToRchMother(record), false, RejectionReasons.ACTIVE_CHILD_PRESENT.toString(), action));
                }
                return false;
            }
            subscription = subscriberService.updateMotherSubscriber(msisdn, mother, lmp);
        } else {
            Long caseNo = (Long) record.get(KilkariConstants.CASE_NO);
            // validate caseNo
            if (!validateCaseNo(caseNo, msisdn, mother, SubscriptionPackType.PREGNANCY, beneficiaryId, importOrigin)) {
                if (flagForMcts) {
                    motherRejectionService.createOrUpdateMother(motherRejectionMcts(convertMapToMother(record), false, RejectionReasons.INVALID_CASE_NO.toString(), action));
                } else {
                    motherRejectionService.createOrUpdateMother(motherRejectionRch(convertMapToRchMother(record), false, RejectionReasons.INVALID_CASE_NO.toString(), action));
                }
                return false;
            }
            Boolean deactivate = ((abortion != null) && abortion) || ((stillBirth != null) && stillBirth) || ((death != null) && death);  // NO CHECKSTYLE Boolean Expression Complexity
            subscription = subscriberService.updateRchMotherSubscriber(msisdn, mother, lmp, caseNo, deactivate);
        }

        // We rejected the update/create for the subscriber
        if (subscription == null) {
            if (flagForMcts) {
                motherRejectionService.createOrUpdateMother(motherRejectionMcts(convertMapToMother(record), false, RejectionReasons.SUBSCRIPTION_REJECTED.toString(), action));
            } else {
                motherRejectionService.createOrUpdateMother(motherRejectionRch(convertMapToRchMother(record), false, RejectionReasons.SUBSCRIPTION_REJECTED.toString(), action));
            }
            return false;
        }

        if ((abortion != null) && abortion) {
            subscriptionService.deactivateSubscription(subscription, DeactivationReason.MISCARRIAGE_OR_ABORTION);
            if (flagForMcts) {
                motherRejectionService.createOrUpdateMother(motherRejectionMcts(convertMapToMother(record), true, null, action));
            } else {
                motherRejectionService.createOrUpdateMother(motherRejectionRch(convertMapToRchMother(record), true, null, action));
            }
            return true;
        }
        if ((stillBirth != null) && stillBirth) {
            subscriptionService.deactivateSubscription(subscription, DeactivationReason.STILL_BIRTH);
            if (flagForMcts) {
                motherRejectionService.createOrUpdateMother(motherRejectionMcts(convertMapToMother(record), true, null, action));
            } else {
                motherRejectionService.createOrUpdateMother(motherRejectionRch(convertMapToRchMother(record), true, null, action));
            }
            return true;
        }
        if ((death != null) && death) {
            subscriptionService.deactivateSubscription(subscription, DeactivationReason.MATERNAL_DEATH);
        }

        if (flagForMcts) {
            motherRejectionService.createOrUpdateMother(motherRejectionMcts(convertMapToMother(record), true, null, action));
        } else {
            motherRejectionService.createOrUpdateMother(motherRejectionRch(convertMapToRchMother(record), true, null, action));
        }
        return true;
    }

    @Override // NO CHECKSTYLE Cyclomatic Complexity
    @Transactional
    public boolean importChildRecord(Map<String, Object> record, SubscriptionOrigin importOrigin) { //NOPMD NcssMethodCount
        if (childPack == null) {
            childPack = subscriptionService.getSubscriptionPack(SubscriptionPackType.CHILD);
        }
        MctsChild child;
        Long msisdn;
        MctsMother mother;
        LocalDate lastUpdateDateNic;
        String childId;
        String action = "";
        Boolean flagForMcts = true;

        if (importOrigin.equals(SubscriptionOrigin.MCTS_IMPORT)) {
            action = actionFinderService.childActionFinder(convertMapToChild(record));
            childId = (String) record.get(KilkariConstants.BENEFICIARY_ID);
            child = mctsBeneficiaryValueProcessor.getOrCreateChildInstance(childId);
            msisdn = (Long) record.get(KilkariConstants.MSISDN);
            mother = (MctsMother) record.get(KilkariConstants.MOTHER_ID);
            lastUpdateDateNic = (LocalDate) record.get(KilkariConstants.LAST_UPDATE_DATE);
        } else {
            flagForMcts = false;
            action = actionFinderService.rchChildActionFinder(convertMapToRchChild(record));
            childId = (String) record.get(KilkariConstants.RCH_ID);
            String mctsId = (String) record.get(KilkariConstants.MCTS_ID);
            child = mctsBeneficiaryValueProcessor.getOrCreateRchChildInstance(childId, mctsId);
            msisdn = (Long) record.get(KilkariConstants.MOBILE_NO);
            mother = mctsBeneficiaryValueProcessor.getOrCreateRchMotherInstance((String) record.get(KilkariConstants.RCH_MOTHER_ID), (String) record.get(KilkariConstants.MCTS_MOTHER_ID));
            lastUpdateDateNic = (LocalDate) record.get(KilkariConstants.EXECUTION_DATE);

        }
        String name = (String) record.get(KilkariConstants.BENEFICIARY_NAME);
        DateTime dob = (DateTime) record.get(KilkariConstants.DOB);
        Boolean death = (Boolean) record.get(KilkariConstants.DEATH);

        //validate mother
        if (!validateMother(mother, child, msisdn, importOrigin)) {
            if (flagForMcts) {
                childRejectionService.createOrUpdateChild(childRejectionMcts(convertMapToChild(record), false, RejectionReasons.MOTHER_ID_ERROR.toString(), action));
            } else {
                childRejectionService.createOrUpdateChild(childRejectionRch(convertMapToRchChild(record), false, RejectionReasons.MOTHER_ID_ERROR.toString(), action));
            }
            return false;
        }

        // validate msisdn
        if (!validateMsisdn(msisdn, SubscriptionPackType.CHILD, childId, importOrigin)) {
            if (flagForMcts) {
                childRejectionService.createOrUpdateChild(childRejectionMcts(convertMapToChild(record), false, RejectionReasons.MSISDN_ALREADY_IN_USE.toString(), action));
            } else {
                childRejectionService.createOrUpdateChild(childRejectionRch(convertMapToRchChild(record), false, RejectionReasons.MSISDN_ALREADY_IN_USE.toString(), action));
            }
            return false;
        }

        // validate dob. We do not sanitize for dob in the future to be in sync with MCTS data
        // NOTE: getId is a way to check for new user. We only accept new children if they have 12 weeks left
        // in the pack. Existing children could have their dob updated to an earlier date
        if (child.getId() == null && !validateReferenceDate(dob, SubscriptionPackType.CHILD, msisdn, childId, importOrigin)) {
            if (flagForMcts) {
                childRejectionService.createOrUpdateChild(childRejectionMcts(convertMapToChild(record), false, RejectionReasons.INVALID_DOB.toString(), action));
            } else {
                childRejectionService.createOrUpdateChild(childRejectionRch(convertMapToRchChild(record), false, RejectionReasons.INVALID_DOB.toString(), action));
            }
            return false;
        }

        // validate and set location
        try {
            MctsBeneficiaryUtils.setLocationFields(locationService.getLocations(record), child);
            MctsBeneficiaryUtils.setLocationFields(locationService.getLocations(record), mother);
        } catch (InvalidLocationException le) {
            LOGGER.error(le.toString());
            subscriptionErrorDataService.create(new SubscriptionError(msisdn, childId,
                    SubscriptionRejectionReason.INVALID_LOCATION, SubscriptionPackType.CHILD, le.getMessage(), importOrigin));
            if (flagForMcts) {
                childRejectionService.createOrUpdateChild(childRejectionMcts(convertMapToChild(record), false, RejectionReasons.INVALID_LOCATION.toString(), action));
            } else {
                childRejectionService.createOrUpdateChild(childRejectionRch(convertMapToRchChild(record), false, RejectionReasons.INVALID_LOCATION.toString(), action));
            }
            return false;
        }

        //validate if it's an updated record compared to one from database
        if (child.getUpdatedDateNic() != null && (lastUpdateDateNic == null || child.getUpdatedDateNic().isAfter(lastUpdateDateNic))) {
            subscriptionErrorDataService.create(new SubscriptionError(msisdn, childId,
                    SubscriptionRejectionReason.BENEFICIARY_ALREADY_SUBSCRIBED, SubscriptionPackType.CHILD,
                    "Updated Record exists", importOrigin));
            if (flagForMcts) {
                childRejectionService.createOrUpdateChild(childRejectionMcts(convertMapToChild(record), false, RejectionReasons.RECORD_ALREADY_EXISTS.toString(), action));
            } else {
                childRejectionService.createOrUpdateChild(childRejectionRch(convertMapToRchChild(record), false, RejectionReasons.RECORD_ALREADY_EXISTS.toString(), action));
            }
            return false;
        }

        List<DeactivatedBeneficiary> deactivatedUsers = deactivatedBeneficiaryDataService.findByExternalId(childId);
        if (deactivatedUsers != null && deactivatedUsers.size() > 0) {
            for (DeactivatedBeneficiary deactivatedUser : deactivatedUsers) {
                if (deactivatedUser.getOrigin() == importOrigin) {
                    String message = deactivatedUser.isCompletedSubscription() ? "Subscription completed" : "User deactivated";
                    if (message.length() > 2) {
                        subscriptionErrorDataService.create(new SubscriptionError(msisdn, childId,
                                SubscriptionRejectionReason.BENEFICIARY_ALREADY_SUBSCRIBED, SubscriptionPackType.CHILD, message, importOrigin));
                        if (flagForMcts) {
                            childRejectionService.createOrUpdateChild(childRejectionMcts(convertMapToChild(record), false, RejectionReasons.RECORD_ALREADY_EXISTS.toString(), action));
                        } else {
                            childRejectionService.createOrUpdateChild(childRejectionRch(convertMapToRchChild(record), false, RejectionReasons.RECORD_ALREADY_EXISTS.toString(), action));
                        }
                        return false;
                    }
                }
            }
        }

        child.setName(name);
        child.setMother(mother);
        child.setUpdatedDateNic(lastUpdateDateNic);

        Subscription childSubscription;
        if (importOrigin.equals(SubscriptionOrigin.MCTS_IMPORT)) {
            childSubscription = subscriberService.updateChildSubscriber(msisdn, child, dob);
        } else {
            childSubscription = subscriberService.updateRchChildSubscriber(msisdn, child, dob);
        }
        // child subscription create/update was rejected
        if (childSubscription == null) {
            if (flagForMcts) {
                childRejectionService.createOrUpdateChild(childRejectionMcts(convertMapToChild(record), false, RejectionReasons.SUBSCRIPTION_REJECTED.toString(), action));
            } else {
                childRejectionService.createOrUpdateChild(childRejectionRch(convertMapToRchChild(record), false, RejectionReasons.SUBSCRIPTION_REJECTED.toString(), action));
            }
            return false;
        }

        // a new child subscription was created -- deactivate mother's pregnancy subscription if she has one
        Subscriber subscriber = childSubscription.getSubscriber();
        Subscription pregnancySubscription = subscriptionService.getActiveSubscription(subscriber,
                SubscriptionPackType.PREGNANCY);
        if (pregnancySubscription != null) {
            subscriptionService.deactivateSubscription(pregnancySubscription, DeactivationReason.LIVE_BIRTH);
        }

        if ((death != null) && death) {
            subscriptionService.deactivateSubscription(childSubscription, DeactivationReason.CHILD_DEATH);
        }

        if (flagForMcts) {
            childRejectionService.createOrUpdateChild(childRejectionMcts(convertMapToChild(record), true, null, action));
        } else {
            childRejectionService.createOrUpdateChild(childRejectionRch(convertMapToRchChild(record), true, null, action));
        }
        return true;
    }

    private boolean validateMother(MctsMother mother, MctsChild child, Long msisdn, SubscriptionOrigin importOrigin) {
        if (mother == null) {
            subscriptionErrorDataService.create(
                    new SubscriptionError(msisdn, importOrigin.equals(SubscriptionOrigin.MCTS_IMPORT) ? child.getBeneficiaryId() : child.getRchId(), SubscriptionRejectionReason.MISSING_MOTHER_ID,
                            SubscriptionPackType.CHILD, "MotherId of child is missing", importOrigin));
            return false;
        } else if (mother != null && child.getMother() != null && !mother.equals(child.getMother())) {
            subscriptionErrorDataService.create(
                    new SubscriptionError(msisdn, importOrigin.equals(SubscriptionOrigin.MCTS_IMPORT) ? child.getBeneficiaryId() : child.getRchId(), SubscriptionRejectionReason.ALREADY_SUBSCRIBED,
                            SubscriptionPackType.CHILD, "Child already registered with different Mother", importOrigin));
            return false;
        }
        return true;
    }

    private boolean validateMsisdn(Long msisdn, SubscriptionPackType packType, String beneficiaryId, SubscriptionOrigin importOrigin) {
        if (msisdn == null) {
            subscriptionErrorDataService.create(
                    new SubscriptionError(-1, beneficiaryId, SubscriptionRejectionReason.MISSING_MSISDN, packType, "", importOrigin));
            return false;
        }

        return true;
    }

    private boolean validateCaseNo(Long caseNo, Long msisdn, MctsMother mother, SubscriptionPackType packType, String beneficiaryId, SubscriptionOrigin importOrigin) {
        if (caseNo == null || (caseNo <= 0)) {
            subscriptionErrorDataService.create(
                    new SubscriptionError(msisdn, beneficiaryId, SubscriptionRejectionReason.INVALID_CASE_NO, packType, "", importOrigin));
            return false;
        }

        if (mother.getMaxCaseNo() != null && caseNo < mother.getMaxCaseNo()) {
            subscriptionErrorDataService.create(new SubscriptionError(msisdn, beneficiaryId,
                    SubscriptionRejectionReason.INVALID_CASE_NO, SubscriptionPackType.PREGNANCY, "Case no is less than the maxCaseNo encountered so far", importOrigin));
            return false;
        }

        return true;
    }

    private boolean childAlreadyPresent(final String motherBenificiaryId) {
        //Found mother by beneficiary id. If there is no mother already present,then import will
        //go to the next check. Else we get the subscriber by the mother id
        //and check if the child subscription is ACTIVE. If yes we do not update the mother.
        MctsMother mctsMother = null;

        try {
            mctsMother = mctsMotherDataService.findByBeneficiaryId(motherBenificiaryId);

            if (mctsMother == null) {
                return false;
            } else {
                Long motherId = mctsMother.getId();
                Subscriber subscriber = subscriberService.getSubscriberByMother(motherId);
                if (subscriber == null) {
                    return false;
                } else {
                    for (Subscription subscription : subscriber.getAllSubscriptions()) {
                        if (subscription.getSubscriptionPack().getType().equals(SubscriptionPackType.CHILD)
                                && subscription.getStatus().equals(SubscriptionStatus.ACTIVE)
                                && subscriber.getChild().getMother() != null
                                && subscriber.getChild().getMother().getBeneficiaryId().equals(motherBenificiaryId)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (MultipleSubscriberException m) {
            LOGGER.error(m.toString());
            return true;
        }
    }

    private boolean validateReferenceDate(DateTime referenceDate, SubscriptionPackType packType, Long msisdn, String beneficiaryId, SubscriptionOrigin importOrigin) {

        if (referenceDate == null) {
            subscriptionErrorDataService.create(
                    new SubscriptionError(msisdn, beneficiaryId,
                            (packType == SubscriptionPackType.PREGNANCY) ?
                                    SubscriptionRejectionReason.MISSING_LMP :
                                    SubscriptionRejectionReason.MISSING_DOB,
                            packType, "", importOrigin));
            return false;
        }

        if (packType == SubscriptionPackType.PREGNANCY) {
            String referenceDateValidationError = pregnancyPack.isReferenceDateValidForPack(referenceDate);
            if (!referenceDateValidationError.isEmpty()) {
                subscriptionErrorDataService.create(
                        new SubscriptionError(msisdn, beneficiaryId, SubscriptionRejectionReason.INVALID_LMP, SubscriptionPackType.PREGNANCY, referenceDateValidationError, importOrigin));
                return false;
            }
        } else { // childPack
            String referenceDateValidationError = childPack.isReferenceDateValidForPack(referenceDate);
            if (!referenceDateValidationError.isEmpty()) {
                subscriptionErrorDataService.create(
                        new SubscriptionError(msisdn, beneficiaryId, SubscriptionRejectionReason.INVALID_DOB, SubscriptionPackType.CHILD, referenceDateValidationError, importOrigin));
                return false;
            }
        }

        return true;
    }

    private Map<String, CellProcessor> getMotherProcessorMapping() {
        Map<String, CellProcessor> mapping = new HashMap<>();

        MctsBeneficiaryUtils.getBeneficiaryLocationMapping(mapping);
        getMotherMapping(mapping);

        mapping.put(KilkariConstants.BENEFICIARY_ID, new Optional(new GetString()));
        mapping.put(KilkariConstants.MSISDN, new Optional(new GetInstanceByString<Long>() {
            @Override
            public Long retrieve(String value) {
                return mctsBeneficiaryValueProcessor.getMsisdnByString(value);
            }
        }));
        mapping.put(KilkariConstants.ABORTION, new Optional(new GetInstanceByString<Boolean>() {
            @Override
            public Boolean retrieve(String value) {
                return mctsBeneficiaryValueProcessor.getAbortionDataFromString(value);
            }
        }));
        mapping.put(KilkariConstants.STILLBIRTH, new Optional(new GetInstanceByString<Boolean>() {
            @Override
            public Boolean retrieve(String value) {
                return mctsBeneficiaryValueProcessor.getStillBirthFromString(value);
            }
        }));
        mapping.put(KilkariConstants.LAST_UPDATE_DATE, new Optional(new GetInstanceByString<LocalDate>() {
            @Override
            public LocalDate retrieve(String value) {
                return (LocalDate) mctsBeneficiaryValueProcessor.getDateByString(value).toLocalDate();
            }
        }));
        return mapping;
    }

    private void getMotherMapping(Map<String, CellProcessor> mapping) {

        mapping.put(KilkariConstants.BENEFICIARY_NAME, new GetString());
        mapping.put(KilkariConstants.LMP, new Optional(new GetInstanceByString<DateTime>() {
            @Override
            public DateTime retrieve(String value) {
                return mctsBeneficiaryValueProcessor.getDateByString(value);
            }
        }));
        mapping.put(KilkariConstants.MOTHER_DOB, new Optional(new GetInstanceByString<DateTime>() {
            @Override
            public DateTime retrieve(String value) {
                return mctsBeneficiaryValueProcessor.getDateByString(value);
            }
        }));
        mapping.put(KilkariConstants.DEATH, new Optional(new GetInstanceByString<Boolean>() {
            @Override
            public Boolean retrieve(String value) {
                return mctsBeneficiaryValueProcessor.getDeathFromString(value);
            }
        }));
    }

    private Map<String, CellProcessor> getRchMotherProcessorMapping() {
        Map<String, CellProcessor> mapping = new HashMap<>();

        MctsBeneficiaryUtils.getBeneficiaryLocationMapping(mapping);
        getMotherMapping(mapping);

        mapping.put(KilkariConstants.RCH_ID, new Optional(new GetString()));
        mapping.put(KilkariConstants.MCTS_ID, new Optional(new GetString()));
        mapping.put(KilkariConstants.MOBILE_NO, new Optional(new GetInstanceByString<Long>() {
            @Override
            public Long retrieve(String value) {
                return mctsBeneficiaryValueProcessor.getMsisdnByString(value);
            }
        }));
        mapping.put(KilkariConstants.ABORTION_TYPE, new Optional(new GetInstanceByString<Boolean>() {
            @Override
            public Boolean retrieve(String value) {
                return mctsBeneficiaryValueProcessor.getAbortionDataFromString(value);
            }
        }));
        mapping.put(KilkariConstants.DELIVERY_OUTCOMES, new Optional(new GetInstanceByString<Boolean>() {
            @Override
            public Boolean retrieve(String value) {
                return mctsBeneficiaryValueProcessor.getStillBirthFromString(value);
            }
        }));
        mapping.put(KilkariConstants.EXECUTION_DATE, new Optional(new GetInstanceByString<LocalDate>() {
            @Override
            public LocalDate retrieve(String value) {
                return (LocalDate) mctsBeneficiaryValueProcessor.getDateByString(value).toLocalDate();
            }
        }));
        mapping.put(KilkariConstants.CASE_NO, new Optional(new GetInstanceByString<Long>() {
            @Override
            public Long retrieve(String value) {
                return mctsBeneficiaryValueProcessor.getCaseNoByString(value);
            }
        }));
        return mapping;
    }

    private Map<String, CellProcessor> getChildProcessorMapping() {
        Map<String, CellProcessor> mapping = new HashMap<>();
        getChildMapping(mapping);

        MctsBeneficiaryUtils.getBeneficiaryLocationMapping(mapping);

        mapping.put(KilkariConstants.BENEFICIARY_ID, new Optional(new GetString()));
        mapping.put(KilkariConstants.MOTHER_ID, new Optional(new GetInstanceByString<MctsMother>() {
            @Override
            public MctsMother retrieve(String value) {
                return mctsBeneficiaryValueProcessor.getMotherInstanceByBeneficiaryId(value);
            }
        }));
        mapping.put(KilkariConstants.MSISDN, new Optional(new GetInstanceByString<Long>() {
            @Override
            public Long retrieve(String value) {
                return mctsBeneficiaryValueProcessor.getMsisdnByString(value);
            }
        }));
        mapping.put(KilkariConstants.LAST_UPDATE_DATE, new Optional(new GetInstanceByString<LocalDate>() {
            @Override
            public LocalDate retrieve(String value) {
                return (LocalDate) mctsBeneficiaryValueProcessor.getDateByString(value).toLocalDate();
            }
        }));
        return mapping;
    }

    private void getChildMapping(Map<String, CellProcessor> mapping) {
        mapping.put(KilkariConstants.BENEFICIARY_NAME, new GetString());
        mapping.put(KilkariConstants.DOB, new Optional(new GetInstanceByString<DateTime>() {
            @Override
            public DateTime retrieve(String value) {
                return mctsBeneficiaryValueProcessor.getDateByString(value);
            }
        }));
        mapping.put(KilkariConstants.DEATH, new Optional(new GetInstanceByString<Boolean>() {
            @Override
            public Boolean retrieve(String value) {
                return mctsBeneficiaryValueProcessor.getDeathFromString(value);
            }
        }));
    }

    private Map<String, CellProcessor> getRchChildProcessorMapping() {
        Map<String, CellProcessor> mapping = new HashMap<>();
        getChildMapping(mapping);

        MctsBeneficiaryUtils.getBeneficiaryLocationMapping(mapping);

        mapping.put(KilkariConstants.MCTS_ID, new Optional(new GetString()));
        mapping.put(KilkariConstants.MCTS_MOTHER_ID, new Optional(new GetString()));
        mapping.put(KilkariConstants.MOBILE_NO, new Optional(new GetInstanceByString<Long>() {
            @Override
            public Long retrieve(String value) {
                return mctsBeneficiaryValueProcessor.getMsisdnByString(value);
            }
        }));
        mapping.put(KilkariConstants.RCH_ID, new Optional(new GetString()));
        mapping.put(KilkariConstants.RCH_MOTHER_ID, new Optional(new GetString()));
        mapping.put(KilkariConstants.EXECUTION_DATE, new Optional(new GetInstanceByString<LocalDate>() {
            @Override
            public LocalDate retrieve(String value) {
                return (LocalDate) mctsBeneficiaryValueProcessor.getDateByString(value).toLocalDate();
            }
        }));

        return mapping;
    }
}
