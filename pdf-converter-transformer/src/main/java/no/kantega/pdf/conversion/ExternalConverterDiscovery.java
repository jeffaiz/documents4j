package no.kantega.pdf.conversion;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;

enum ExternalConverterDiscovery {

    MICROSOFT_WORD("no.kantega.pdf.conversion.msoffice.MicrosoftWordBridge");
    private final String className;

    private ExternalConverterDiscovery(String className) {
        this.className = className;
    }

    private static IExternalConverter make(Class<? extends IExternalConverter> externalConverterClass,
                                           File baseFolder, long processTimeout, TimeUnit timeUnit) {
        try {
            return externalConverterClass
                    .getConstructor(File.class, long.class, TimeUnit.class)
                    .newInstance(baseFolder, processTimeout, timeUnit);
        } catch (Exception e) {
            throw new LinkageError(String.format("The class path contains an external converter with an " +
                    "invalid constructor signature: %s", e.getMessage()));
        }
    }

    private static Set<IExternalConverter> makeAll(Set<Class<? extends IExternalConverter>> externalConverterClasses,
                                                   File baseFolder, long processTimeout, TimeUnit timeUnit) {
        Set<IExternalConverter> externalConverters = new HashSet<IExternalConverter>();
        for (Class<? extends IExternalConverter> externalConverterClass : externalConverterClasses) {
            externalConverters.add(make(externalConverterClass, baseFolder, processTimeout, timeUnit));
        }
        return externalConverters;
    }

    private static Set<Class<? extends IExternalConverter>> discover(
            Map<Class<? extends IExternalConverter>, Boolean> externalConverterRegistration) {
        Set<Class<? extends IExternalConverter>> discovered = new HashSet<Class<? extends IExternalConverter>>();
        Map<String, ExternalConverterDiscovery> autoDetectNameMap = makeAutoDetectNameMap();
        for (Map.Entry<Class<? extends IExternalConverter>, Boolean> registration : externalConverterRegistration.entrySet()) {
            if (registration.getValue()) {
                discovered.add(registration.getKey());
            } else {
                autoDetectNameMap.remove(registration.getKey().getName());
            }
        }
        for (ExternalConverterDiscovery autoDetect : autoDetectNameMap.values()) {
            Class<? extends IExternalConverter> externalConverterClass = autoDetect.tryFindClass();
            if (externalConverterClass != null) {
                discovered.add(externalConverterClass);
            }
        }
        return discovered;
    }

    private static Set<Class<? extends IExternalConverter>> validate(Set<Class<? extends IExternalConverter>> externalConverterClasses) {
        if (externalConverterClasses.size() == 0) {
            throw new LinkageError("There were no external converters found on the class path.");
        }
        return externalConverterClasses;
    }

    private static Map<String, ExternalConverterDiscovery> makeAutoDetectNameMap() {
        Map<String, ExternalConverterDiscovery> autoDetectNames = new HashMap<String, ExternalConverterDiscovery>();
        for (ExternalConverterDiscovery autoDetect : ExternalConverterDiscovery.values()) {
            autoDetectNames.put(autoDetect.getClassName(), autoDetect);
        }
        return autoDetectNames;
    }

    public static Set<IExternalConverter> loadConfiguration(Map<Class<? extends IExternalConverter>, Boolean> externalConverterRegistration,
                                                            File baseFolder, long processTimeout, TimeUnit timeUnit) {
        return makeAll(validate(discover(externalConverterRegistration)), baseFolder, processTimeout, timeUnit);
    }

    protected String getClassName() {
        return className;
    }

    @SuppressWarnings("unchecked")
    protected Class<? extends IExternalConverter> tryFindClass() {
        try {
            Class<?> foundClass = Class.forName(getClassName(), false, getClass().getClassLoader());
            checkState(IExternalConverter.class.isAssignableFrom(foundClass), "Illegal auto discovery class implementation found");
            return (Class<? extends IExternalConverter>) foundClass;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
