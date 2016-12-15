package org.motechproject.nms.kilkari.utils;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Constant field names for MCTS beneficiary import.
 */
public final class KilkariConstants {

    public static final String BENEFICIARY_ID = "ID_No";
    public static final String BENEFICIARY_NAME = "Name";
    public static final String MSISDN = "Whom_PhoneNo";
    public static final String LMP = "LMP_Date";
    public static final String DOB = "Birthdate";
    public static final String MOTHER_ID = "Mother_ID";
    public static final String MOTHER_DOB = "Birthdate";
    public static final String ABORTION = "Abortion";
    public static final String STILLBIRTH = "Outcome_Nos";
    public static final String DEATH = "Entry_Type";
    public static final String STATE_ID = "StateID";
    public static final String DISTRICT_ID = "District_ID";
    public static final String DISTRICT_NAME = "District_Name";
    public static final String TALUKA_ID = "Taluka_ID";
    public static final String TALUKA_NAME = "Taluka_Name";
    public static final String HEALTH_BLOCK_ID = "HealthBlock_ID";
    public static final String HEALTH_BLOCK_NAME = "HealthBlock_Name";
    public static final String PHC_ID = "PHC_ID";
    public static final String PHC_NAME = "PHC_Name";
    public static final String SUB_CENTRE_ID = "SubCentre_ID";
    public static final String SUB_CENTRE_NAME = "SubCentre_Name";
    public static final String CENSUS_VILLAGE_ID = "Village_ID";
    public static final String VILLAGE_NAME = "Village_Name";
    public static final String NON_CENSUS_VILLAGE_ID = "SVID";
    public static final String CIRCLE_99 = "99";
    public static final String IMPORTED = "Imported {}";
    public static final String REJECTED = "Rejected {}";
    public static final String LAST_UPDATE_DATE = "Last_Update_Date";

    public static final String MCTS_ID = "MCTS_ID_No";
    public static final String RCH_ID = "Registration_no";
    public static final String MCTS_MOTHER_ID = "MCTS_Mother_ID_No";
    public static final String RCH_MOTHER_ID = "Mother_Registration_no";
    public static final String CASE_NO = "Case_no";
    public static final String MOBILE_NO = "Mobile_no";
    public static final String ABORTION_TYPE = "Abortion_Type";
    public static final String DELIVERY_OUTCOMES = "Delivery_Outcomes";
    public static final String EXECUTION_DATE = "Exec_date";

    public static final String UPDATE_SR_NO = "Sr No";
    public static final String UPDATE_MCTS_ID = "MCTS ID";
    public static final String UPDATE_STATE_MCTS_ID = "STATE ID";
    public static final String UPDATE_DOB = "Beneficiary New DOB change";
    public static final String UPDATE_LMP = "Beneficiary New LMP change";
    public static final String UPDATE_MSISDN = "Beneficiary New Mobile no change";

    public static final String MAPPER_STATE = "StateID";
    public static final String MAPPER_DISTRICT = "District_ID";
    public static final String MAPPER_TALUKA = "Taluka_ID";
    public static final String MAPPER_HEALTH_BLOCK = "HealthBlock_ID";
    public static final String MAPPER_PHC = "PHC_ID";
    public static final String MAPPER_SUBCENTRE = "SubCentre_ID";
    public static final String MAPPER_CENSUS_VILLAGE = "Village_ID";
    public static final String MAPPER_NON_CENSUS_VILLAGE = "SVID";

    // Time format constants
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.S");

    // Numerical constants
    public static final long TEN_DIGITS_MAX = 10000000000L;
    public static final int PROGRESS_INTERVAL = 100;
    public static final int MAX_CHAR_ALERT = 4900;
    public static final int THREE_MONTHS = 90;
    public static final int DAY_IN_WEEK = 7;
    public static final int PREGNANCY_PACK_LENGTH_WEEKS = 72;
    public static final int PREGNANCY_PACK_LENGTH_DAYS = PREGNANCY_PACK_LENGTH_WEEKS * DAY_IN_WEEK;
    public static final int CHILD_PACK_LENGTH_WEEKS = 48;
    public static final int CHILD_PACK_LENGTH_DAYS = CHILD_PACK_LENGTH_WEEKS * DAY_IN_WEEK;
    public static final Long DEFAULT_MAX_ACTIVE_SUBSCRIPTION_CAP = 1000000L;
    public static final int MSISDN_LENGTH = 10;

    // SQL constants
    public static final String SELECT_SUBSCRIBERS_BY_NUMBER = "select * from nms_subscribers where callingNumber = ?";
    public static final String SELECT_SUBSCRIBERS_BY_MOTHER_ID_OID = "select * from nms_subscribers where mother_id_OID = ?";

    // Log constants
    public static final String MORE_THAN_ONE_SUBSCRIBER_WITH_SAME_NUMBER = "More than one subscriber returned for callingNumber %s";
    public static final String MORE_THAN_ONE_SUBSCRIBER_WITH_SAME_MOTHERID = "More than one subscriber returned for motherID %s";
    public static final String SUBSCRIBER_NOT_FOUND = "callingNumber %s not Found";
    public static final String SQL_QUERY_LOG = "SQL QUERY: {}";

    // Message constants
    public static final String PACK_CACHE_EVICT_MESSAGE_SUBJECT = "nms.kilkari.cache.evict.pack";
    public static final String SUBSCRIPTION_UPKEEP_SUBJECT = "nms.kilkari.upkeep_subscriptions";
    public static final String NMS_IMI_KK_PROCESS_CSR_SUBJECT = "nms.imi.kk.process_csr";
    public static final String CSR_VERIFIER_CACHE_EVICT_SUBJECT = "nms.kk.cache.evict.csv_verifier";
    public static final String CIRCLE_CACHE_EVICT_SUBJECT = "nms.region.cache.evict.language";
    public static final String LANGUAGE_CACHE_EVICT_SUBJECT = "nms.region.cache.evict.language";
    public static final String TOGGLE_SUBSCRIPTION_CAPPING = "nms.kilkari.subscription.capping";
    public static final String TOGGLE_CAP_KEY = "nms.kilkari.subscription.cap.key";

    // Settings constants
    public static final String WEEKS_TO_KEEP_CLOSED_SUBSCRIPTIONS = "kilkari.weeks_to_keep_closed_subscriptions";
    public static final String SUBSCRIPTION_CAP = "kilkari.subscription.cap";
    public static final String SUBSCRIPTION_MANAGER_CRON = "kilkari.subscription.manager.cron";

    private KilkariConstants() {
    }
}
