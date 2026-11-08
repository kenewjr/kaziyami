package app.kaziyami.test;

import java.lang.reflect.Method;
import java.util.List;

public class SourceInstantiationRunner {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: SourceInstantiationRunner <extClass>");
            System.exit(1);
        }
        String extClass = args[0];
        try {
            Class<?> clazz = Class.forName(extClass);
            Object instance = clazz.getDeclaredConstructor().newInstance();

            boolean isFactory = false;
            for (Class<?> iface : clazz.getInterfaces()) {
                if ("eu.kanade.tachiyomi.animesource.AnimeSourceFactory".equals(iface.getName())) {
                    isFactory = true;
                    break;
                }
            }

            if (isFactory) {
                Method createSources = clazz.getMethod("createSources");
                List<?> sources = (List<?>) createSources.invoke(instance);
                for (Object src : sources) {
                    printSource(src);
                }
            } else {
                printSource(instance);
            }
        } catch (Throwable t) {
            System.err.println("INSTANTIATION_FAILURE: " + t.getMessage());
            t.printStackTrace(System.err);
            System.exit(2);
        }
    }

    private static void printSource(Object src) throws Exception {
        Class<?> sc = src.getClass();
        String name = (String) sc.getMethod("getName").invoke(src);
        String lang = (String) sc.getMethod("getLang").invoke(src);
        long id = (Long) sc.getMethod("getId").invoke(src);
        System.out.println("SOURCE|" + name + "|" + lang + "|" + id);
    }
}

