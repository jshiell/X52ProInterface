package org.infernus.x52pro;

import javax.usb.*;
import java.util.List;

/**
 *
 */
public class UsbRootHub {
    private final UsbHub rootHub;

    public UsbRootHub() throws UsbException {
        rootHub = UsbHostManager.getUsbServices().getRootUsbHub();
    }

    public UsbDevice findDevice(final short vendorId, final short productId) {
        return findDevice(rootHub, vendorId, productId);
    }

    @SuppressWarnings("unchecked")
    private UsbDevice findDevice(final UsbHub hub, final short vendorId, final short productId) {
        for (UsbDevice device : (List<UsbDevice>) hub.getAttachedUsbDevices()) {
            final UsbDeviceDescriptor desc = device.getUsbDeviceDescriptor();
            if (desc.idVendor() == vendorId && desc.idProduct() == productId) {
                return device;
            }

            if (device.isUsbHub()) {
                device = findDevice((UsbHub) device, vendorId, productId);
                if (device != null) {
                    return device;
                }
            }
        }

        return null;
    }

}
