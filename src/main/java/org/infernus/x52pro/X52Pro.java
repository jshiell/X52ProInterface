package org.infernus.x52pro;

import javax.usb.UsbConst;
import javax.usb.UsbControlIrp;
import javax.usb.UsbDevice;
import javax.usb.UsbException;
import java.util.Calendar;
import java.util.Optional;

/**
 * An os-agnostic controller for the X52Pro.
 * <p/>
 * All credit for reverse engineering the protocol goes to https://github.com/nirenjan
 *
 * @see "https://github.com/nirenjan/x52-mac/blob/master/vendor_api.md"
 */
public class X52Pro {
    private static final short VENDOR_ID = 0x06A3;
    private static final short PRODUCT_ID = 0x0762;

    private static final byte REQUEST_TYPE = (byte) 0x91;

    private static final short INDEX_UPDATE_PRIMARY_CLOCK = 0xc0;
    private static final short INDEX_UPDATE_DATE_DAYMONTH = 0xc4;
    private static final short INDEX_UPDATE_DATE_YEAR = 0xc8;
    private static final short INDEX_DELETE_LINE1 = 0xd9;
    private static final short INDEX_DELETE_LINE2 = 0xda;
    private static final short INDEX_DELETE_LINE3 = 0xdc;
    private static final short INDEX_APPEND_LINE1 = 0xd1;
    private static final short INDEX_APPEND_LINE2 = 0xd2;
    private static final short INDEX_APPEND_LINE3 = 0xd4;

    private static final int MESSAGE_WAIT_TIMEOUT = 100;
    private static final int MAX_CHARACTERS_PER_LINE = 16;

    private final UsbDevice device;

    private short lastTimeWritten = 0;
    private short lastDayMonthWritten = 0;
    private short lastYearWritten = 0;

    public static Optional<X52Pro> findDevice(final UsbRootHub rootHub) {
        if (rootHub == null) {
            throw new IllegalArgumentException("rootHub must not be null");
        }

        final UsbDevice locatedDevice = rootHub.findDevice(VENDOR_ID, PRODUCT_ID);
        if (locatedDevice != null) {
            return Optional.of(new X52Pro(locatedDevice));
        }
        return Optional.empty();
    }

    public X52Pro(final UsbDevice device) {
        if (device == null) {
            throw new IllegalArgumentException("device may not be null");
        }

        this.device = device;
    }

    public void writeLine(final int line,
                          final byte[] text) throws UsbException {
        if (line < 1 || line > 3) {
            throw new IllegalArgumentException("Line must be 1-3");
        }

        sendControlMessage(deleteIndexForLine(line), (short) 0);

        if (text == null || text.length == 0) {
            return;
        }

        for (int i = 0; i < text.length && i < MAX_CHARACTERS_PER_LINE; i += 2) {
            short textValue = 0;
            textValue |= text[i];
            if (i + i < text.length) {
                textValue &= (short) ~(255 << 8);
                textValue |= (text[i + 1] << 8);
            }
            sendControlMessage(appendIndexForLine(line), textValue);
        }
    }

    public void updateTime(final Calendar cal,
                           final ClockTimeFormat clockTimeFormat,
                           final boolean forceUpdate)
            throws UsbException {
        if (cal == null) {
            throw new IllegalArgumentException("cal must not be null");
        }
        if (clockTimeFormat == null) {
            throw new IllegalArgumentException("clockTimeFormat must not be null");
        }

        short timeValue = 0;
        timeValue |= (short) cal.get(Calendar.MINUTE);

        timeValue &= (short) ~(255 << 8);
        timeValue |= (short) (cal.get(Calendar.HOUR_OF_DAY) << 8);

        timeValue &= (short) ~(1 << 15);
        if (clockTimeFormat == ClockTimeFormat.CLOCK_24_HOUR) {
            timeValue |= (short) (1 << 15);
        }

        if (forceUpdate || timeValue != lastTimeWritten) {
            sendControlMessage(INDEX_UPDATE_PRIMARY_CLOCK, timeValue);
        }
        lastTimeWritten = timeValue;
    }

    public void updateDate(final Calendar cal,
                           final ClockDateFormat clockDateFormat,
                           final boolean forceUpdate)
            throws UsbException {
        if (cal == null) {
            throw new IllegalArgumentException("cal must not be null");
        }
        if (clockDateFormat == null) {
            throw new IllegalArgumentException("clockDateFormat must not be null");
        }

        final short[] clockValues = currentClockValues(cal, clockDateFormat);

        short dayMonthValue = clockValues[0];
        dayMonthValue &= (short) ~(255 << 8);
        dayMonthValue |= (short) (clockValues[1] << 8);

        if (forceUpdate || dayMonthValue != lastDayMonthWritten) {
            sendControlMessage(INDEX_UPDATE_DATE_DAYMONTH, dayMonthValue);
        }

        if (forceUpdate || clockValues[2] != lastYearWritten) {
            sendControlMessage(INDEX_UPDATE_DATE_YEAR, clockValues[2]);
        }

        lastDayMonthWritten = dayMonthValue;
        lastYearWritten = clockValues[2];
    }

    private short deleteIndexForLine(final int lineNumber) {
        switch (lineNumber) {
            case 1:
                return INDEX_DELETE_LINE1;
            case 2:
                return INDEX_DELETE_LINE2;
            case 3:
                return INDEX_DELETE_LINE3;
            default:
                throw new IllegalArgumentException("Invalid line number: " + lineNumber);
        }
    }

    private short appendIndexForLine(final int lineNumber) {
        switch (lineNumber) {
            case 1:
                return INDEX_APPEND_LINE1;
            case 2:
                return INDEX_APPEND_LINE2;
            case 3:
                return INDEX_APPEND_LINE3;
            default:
                throw new IllegalArgumentException("Invalid line number: " + lineNumber);
        }
    }

    private void sendControlMessage(final short index,
                                    final short value)
            throws UsbException {
        final UsbControlIrp irp = device.createUsbControlIrp(UsbConst.REQUESTTYPE_TYPE_VENDOR, REQUEST_TYPE, value, index);
        irp.setData(new byte[1]); // javax.usb appears to dislike 0 length buffers
        device.asyncSubmit(irp);

        // sending another message before this is complete leads to ignored messages
        irp.waitUntilComplete(MESSAGE_WAIT_TIMEOUT);
    }

    private short[] currentClockValues(final Calendar cal, final ClockDateFormat clockDateFormat) {
        final short[] clockValues = new short[3];

        switch (clockDateFormat) {
            case DATE_DD_MM_YYYY:
                clockValues[0] = (short) cal.get(Calendar.DAY_OF_MONTH);
                clockValues[1] = (short) (cal.get(Calendar.MONTH) + 1);
                clockValues[2] = (short) cal.get(Calendar.YEAR);
                break;

            case DATE_MM_DD_YYYY:
                clockValues[0] = (short) (cal.get(Calendar.MONTH) + 1);
                clockValues[1] = (short) cal.get(Calendar.DAY_OF_MONTH);
                clockValues[2] = (short) cal.get(Calendar.YEAR);
                break;

            case DATE_YYYY_MM_DD:
                clockValues[0] = (short) cal.get(Calendar.YEAR);
                clockValues[1] = (short) (cal.get(Calendar.MONTH) + 1);
                clockValues[2] = (short) cal.get(Calendar.DAY_OF_MONTH);
                break;

            default:
                throw new IllegalArgumentException("Unknown clock date format: " + clockDateFormat);
        }

        return clockValues;
    }

}
