package org.motechproject.nms.imi.service.impl;

import org.joda.time.DateTime;
import org.motechproject.nms.imi.domain.CallDetailRecord;
import org.motechproject.nms.kilkari.dto.CallDetailRecordDto;
import org.motechproject.nms.kilkari.exception.InvalidCallRecordDataException;
import org.motechproject.nms.props.domain.CallDisconnectReason;
import org.motechproject.nms.props.domain.RequestId;
import org.motechproject.nms.props.domain.StatusCode;
import org.motechproject.nms.props.domain.WhatsAppOptInStatusCode;

/**
 * Helper class to parse a CDR CSV line to a CallDetailRecordDTO or a CallDetailRecord
 */
public final class CdrHelper {

    public static final String CDR_HEADER = "RequestId,Msisdn,CallId,AttemptNo,CallStartTime,CallAnswerTime," +
            "CallEndTime,CallDurationInPulse,CallStatus,LanguageLocationId,ContentFile,MsgPlayStartTime," +
            "MsgPlayEndTime,CircleId,OperatorId,Priority,CallDisconnectReason,WeekId,opt_in_call_eligibility,opt_in_input";

    private static final long MIN_MSISDN = 1000000000L;
    private static final long MAX_MSISDN = 9999999999L;

    private enum FieldName {
        REQUEST_ID,
        MSISDN,
        CALL_ID,
        ATTEMPT_NO,
        CALL_START_TIME,
        CALL_ANSWER_TIME,
        CALL_END_TIME,
        CALL_DURATION_IN_PULSE,
        CALL_STATUS,
        LANGUAGE_LOCATION_ID,
        CONTENT_FILE,
        MSG_PLAY_START_TIME,
        MSG_PLAY_END_TIME,
        CIRCLE_ID,
        OPERATOR_ID,
        PRIORITY,
        CALL_DISCONNECT_REASON,
        WEEK_ID,
        OPT_IN_CALL_ELIGIBILITY,
        OPT_IN_INPUT,
        FIELD_COUNT;
    }


    private CdrHelper() { }


    private static long msisdnFromString(String msisdn) {
        try {
            Long l = Long.parseLong(msisdn);
            if (l < MIN_MSISDN || l > MAX_MSISDN) {
                throw new IllegalArgumentException("MSISDN must be >= 1000000000 and <= 9999999999");
            }
            return l;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("MSISDN must be an integer", e);
        }
    }


    private static Long longOrNullFromString(String which, String s) {
        if (s == null || s.isEmpty()) { return null; }

        return longFromString(which, s);
    }


    private static long longFromString(String which, String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format("%s must be an integer", which), e);
        }
    }


    private static int integerFromString(String which, String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format("%s must be an integer", which), e);
        }
    }

    private static int whatsAppStatusvalueFromString(String which, String s) {
        try {
            if (s.equalsIgnoreCase("NULL") || s.isEmpty() || s.equals(" ")){
                return 5;
            }else {
                return Integer.parseInt(s);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format("%s must be an integer or NULL", which), e);
        }
    }


    private static Integer calculateMsgPlayDuration(String msgPlayStartTime, String msgPlayEndTime) {
        Long start;
        Long end;

        try {
            start = longFromString("MsgPlayStartTime", msgPlayStartTime);
            end = longFromString("MsgPlayEndTime", msgPlayEndTime);
        } catch (IllegalArgumentException e) {
            // MsgPlayStart is optional, so if either is missing return the play time as 0
            return 0;
        }

        if (end < start) {
            throw new IllegalArgumentException("MsgPlayEndTime cannot be before MsgPlayStartTime");
        }

        if ((end - start) > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "The difference between MsgPlayEndTime and MsgPlayStartTime is too large");
        }

        return ((int) (end - start));
    }


    /**
     * Validate that a CSV live might be a CDR - ie: just count fields...
     *
     * All errors will throw an IllegalArgumentException
     *
     * @param line a CSV line from a CDR Detail File from IMI
     */
    public static void validateCsv(String line) {
        String[] fields = line.split(",");

        if (fields.length != FieldName.FIELD_COUNT.ordinal()) {
            throw new IllegalArgumentException(String.format(
                    "Invalid field count, expecting %d but received %d", FieldName.FIELD_COUNT.ordinal(),
                    fields.length));
        }
    }


    /**
     * Take what we need from an IMI CDRDetail line to make our CDR DTO
     *
     * CSV errors will throw an IllegalArgumentException, others InvalidCsrException
     *
     * @param line a CSV line from a CDR Detail File from IMI
     * @return a CallDetailRecordDto
     */
    public static CallDetailRecordDto csvLineToCdrDto(String line) {
        CallDetailRecordDto cdr = new CallDetailRecordDto();
        String[] fields = line.split(",");

        if (fields.length != FieldName.FIELD_COUNT.ordinal()) {
            throw new IllegalArgumentException(String.format(
                    "Invalid field count, expecting %d but received %d", FieldName.FIELD_COUNT.ordinal(),
                    fields.length));
        }


        /*
         * See API 4.4.3 - CDR Detail File Format
         */

        try {
            cdr.setRequestId(RequestId.fromString(fields[FieldName.REQUEST_ID.ordinal()]));

            cdr.setMsisdn(msisdnFromString(fields[FieldName.MSISDN.ordinal()]));

            Long callAnswerTime = longOrNullFromString("CallAnswerTime",
                    fields[FieldName.CALL_ANSWER_TIME.ordinal()]);
            if (callAnswerTime != null) {
                cdr.setCallAnswerTime(new DateTime(callAnswerTime));
            } else {
                cdr.setCallAnswerTime(null);
            }

            cdr.setMsgPlayDuration(calculateMsgPlayDuration(fields[FieldName.MSG_PLAY_START_TIME.ordinal()],
                    fields[FieldName.MSG_PLAY_END_TIME.ordinal()]));

            cdr.setStatusCode(StatusCode.fromInt(integerFromString("CallStatus",
                    fields[FieldName.CALL_STATUS.ordinal()])));

            cdr.setLanguageLocationId(fields[FieldName.LANGUAGE_LOCATION_ID.ordinal()]);

            cdr.setContentFile(fields[FieldName.CONTENT_FILE.ordinal()]);

            cdr.setCircleId(fields[FieldName.CIRCLE_ID.ordinal()]);

            cdr.setOperatorId(fields[FieldName.OPERATOR_ID.ordinal()]);

            cdr.setCallDisconnectReason(CallDisconnectReason.fromInt(integerFromString("CallDisconnectReason",
                    fields[FieldName.CALL_DISCONNECT_REASON.ordinal()])));

            cdr.setWeekId(fields[FieldName.WEEK_ID.ordinal()]);

            cdr.setOptInEligibility(fields[FieldName.OPT_IN_CALL_ELIGIBILITY.ordinal()]);

            cdr.setOptInStatusCode(WhatsAppOptInStatusCode.fromValue(whatsAppStatusvalueFromString("opt_in_input",fields[FieldName.OPT_IN_INPUT.ordinal()])));

        } catch (IllegalArgumentException e) {
            throw new InvalidCallRecordDataException(e.getMessage(), e);
        }

        return cdr;
    }


    /**
     * Map all IMI CDRDetail fields to a CallDetailRecord - to store for reporting
     *
     * All errors will throw an IllegalArgumentException
     *
     * @param line a CSV line from a CDR Detail File
     * @return a CallDetailRecord
     */
    public static CallDetailRecord csvLineToCdr(String line) {
        CallDetailRecord cdr = new CallDetailRecord();
        String[] fields = line.split(",");

        if (fields.length != FieldName.FIELD_COUNT.ordinal()) {
            throw new IllegalArgumentException(String.format(
                "Invalid field count - expected %d but received %d", FieldName.FIELD_COUNT.ordinal(), fields.length));
        }

        /*
         * See API 4.4.3 - CDR Detail File Format
         */

        cdr.setRequestId(fields[FieldName.REQUEST_ID.ordinal()]);
        cdr.setMsisdn(msisdnFromString(fields[FieldName.MSISDN.ordinal()]));
        cdr.setCallId(fields[FieldName.CALL_ID.ordinal()]);
        cdr.setAttemptNo(fields[FieldName.ATTEMPT_NO.ordinal()]);
        cdr.setCallStartTime(fields[FieldName.CALL_START_TIME.ordinal()]);
        cdr.setCallAnswerTime(fields[FieldName.CALL_ANSWER_TIME.ordinal()]);
        cdr.setCallEndTime(fields[FieldName.CALL_END_TIME.ordinal()]);
        cdr.setCallDurationInPulse(fields[FieldName.CALL_DURATION_IN_PULSE.ordinal()]);
        cdr.setCallStatus(fields[FieldName.CALL_STATUS.ordinal()]);
        cdr.setLanguageLocationId(fields[FieldName.LANGUAGE_LOCATION_ID.ordinal()]);
        cdr.setContentFile(fields[FieldName.CONTENT_FILE.ordinal()]);
        cdr.setMsgPlayStartTime(fields[FieldName.MSG_PLAY_START_TIME.ordinal()]);
        cdr.setMsgPlayEndTime(fields[FieldName.MSG_PLAY_END_TIME.ordinal()]);
        cdr.setCircleId(fields[FieldName.CIRCLE_ID.ordinal()]);
        cdr.setOperatorId(fields[FieldName.OPERATOR_ID.ordinal()]);
        cdr.setPriority(fields[FieldName.PRIORITY.ordinal()]);
        cdr.setCallDisconnectReason(fields[FieldName.CALL_DISCONNECT_REASON.ordinal()]);
        cdr.setWeekId(fields[FieldName.WEEK_ID.ordinal()]);
        cdr.setOpt_in_call_eligibility(Boolean.parseBoolean(fields[FieldName.OPT_IN_CALL_ELIGIBILITY.ordinal()]));
        cdr.setOpt_in_input(WhatsAppOptInStatusCode.fromValue(whatsAppStatusvalueFromString("opt_in_input",fields[FieldName.OPT_IN_INPUT.ordinal()])).toString());

        return cdr;
    }

    /**
     * Validate Header coming in CDR file from IMI
     *
     * @param line a CSV line from a CDR Detail File
     *
     */
    public static void validateHeader(String line) {

        if (!(CDR_HEADER.equalsIgnoreCase(line))) {
            throw new IllegalArgumentException("Invalid CDR header");
        }
    }

}
