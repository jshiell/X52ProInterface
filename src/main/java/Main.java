import org.infernus.x52pro.*;

import java.util.Calendar;
import java.util.Optional;

public class Main {

    public static void main(String[] args) {
        new Main().run();
    }

    public void run() {
        try {
            final UsbRootHub rootHub = new UsbRootHub();
            final Optional<X52Pro> locatedDevice = X52Pro.findDevice(rootHub);
            if (!locatedDevice.isPresent()) {
                System.err.println("Couldn't find X52 Pro");

            } else {
                final X52Pro device = locatedDevice.get();
                final Calendar cal = Calendar.getInstance();
                device.updateDate(cal, ClockDateFormat.DATE_DD_MM_YYYY, false);
                device.updateTime(cal, ClockTimeFormat.CLOCK_24_HOUR, false);

                device.writeLine(1, new byte[] {CharacterMap.SQUARE_ROOT, CharacterMap.ONE, CharacterMap.THREE});
            }

        } catch (Exception e) {
            System.err.println("An error occurred");
            e.printStackTrace(System.err);
        }
    }

}
