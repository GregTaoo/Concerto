package top.gregtao.concerto.util;

import java.util.function.Supplier;
import java.util.logging.Logger;

public class SilentLogger extends Logger {

    public SilentLogger(String name) {
        super(name, null);
    }

//    @Override
//    public void info(Supplier<String> msgSupplier) {
//        // Block any common messages
//    }
}
