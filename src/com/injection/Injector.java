package com.injection;


import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Injector {
    private static String classpath;
    private static final Map<Class, Object> SINGLETON_HOLDER = new HashMap<>();
    private static final Map<Class, Set<Class>> INTERFACES_IMPL = new HashMap<>();
    private static final Function<Class<?>, Object> INSTANCE_THROUGH_DEF_CONSTRUCTOR = clazz -> {
                try {
                    return clazz.getConstructor().newInstance();
                } catch (Exception e) {
                    throw new InjectionError("Could not create instance through def constructor for " + clazz);
                }
            };

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        inject(main);
        System.out.println(main);
    }

    public static void inject(Object parent) throws Exception {
        SINGLETON_HOLDER.put(parent.getClass(), parent);
        // hardcoded
        Injector.classpath = System.getProperty("user.dir") + File.separator + "target" + File.separator + "classes";
        mainPackage();
    }

    private static void mainPackage() throws Exception {
        File file = new File(Injector.classpath);
        List<Class> classes = new ArrayList<>();
        findClassesRecursive(file, classes);
        resolveInjection(classes);
    }

    private static void findClassesRecursive(File file, List<Class> classes) throws ClassNotFoundException {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File fileInDir : files) {
                    findClassesRecursive(fileInDir, classes);
                }
            }
        } else {
            String name = file.getAbsolutePath().replace(Injector.classpath, "")
                    .replace(File.separator, ".")
                    .replaceFirst(".", "");
            if (name.endsWith(".class")) {
                Class clazz = Class.forName(name.replace(".class", ""), false,
                        Thread.currentThread().getContextClassLoader());
                if (clazz.isAnnotationPresent(Bean.class)) {
                    if (clazz.getInterfaces().length != 0) {
                        for (Class i : clazz.getInterfaces()) {
                            classes.add(i);
                            INTERFACES_IMPL.computeIfAbsent(i, k -> new HashSet<>()).add(clazz);
                        }
                        return;
                    }
                    classes.add(clazz);
                }
            }
        }
    }

    private static void resolveInjection(List<Class> classes) throws Exception {
        Map<Class, Set<ClassToInject>> graph = new HashMap<>();
        Map<Class, Integer> priority = new HashMap<>();
        for (Class clazz : classes) {
            Set<ClassToInject> classesWhichMustBeInjected = getClassesWhichMustBeInjected(clazz, priority);
            graph.put(clazz, classesWhichMustBeInjected);
        }
        List<Class> list = priority.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        for (Class whatInject : list) {
            for (Map.Entry<Class, Set<ClassToInject>> entry : graph.entrySet()) {
                ClassToInject value = entry.getValue()
                        .stream()
                        .filter(classToInject -> classToInject.clazz == whatInject)
                        .findAny()
                        .orElse(null);
                if (entry.getKey() == whatInject || value == null) {
                    continue;
                }
                Class whereInject = entry.getKey();
                if (value.injectType == InjectType.FIELD) {
                    resolveFieldInjection(whatInject, whereInject);
                }
                if (value.injectType == InjectType.METHOD) {
                    resolveMethodInjection(whatInject, whereInject);
                }
            }
        }
    }

    private static Object findInstance(Class<?> clazz) {
        return clazz;
    }

    private static Object findImpls(Class<?> i) {
        Set<Class> impls = INTERFACES_IMPL.get(i);
        if (impls.size() == 1) {
            return (Class<?>) impls.iterator().next();
        }
        return impls;
    }

    private static Object findFakeObject(Class clazz) {
        if (clazz.isInterface()) {
            return findImpls(clazz);
        } else {
            return findInstance(clazz);
        }
    }

    private static void resolveFieldInjection(Class whatInject, Class whereInject)
            throws Exception {
        Object what, where;
        if (whatInject.isInterface()) {
            what = findImpls(whatInject);
        } else {
            what = findInstance(whatInject);
        }

        if (whereInject.isInterface()) {
            where = findImpls(whereInject);
        } else {
            where = findInstance(whereInject);
        }

        if (tryToInjectFast(whatInject.getSimpleName(), what, whereInject, where)) {
            return;
        }
        for (Field field : whereInject.getDeclaredFields()) {
            if (field.getDeclaringClass() == whatInject) {
                injectField(field, where, what);
                return;
            }
        }
    }

    private static boolean tryToInjectFast(String name, Object what, Class whereInject, Object where)
            throws Exception {
        Field field;
        name = name.substring(0, 1).toLowerCase() + name.substring(1);
        try {
            field = whereInject.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return false;
        }
        injectField(field, where, what);
        return true;
    }

    private static void injectField(Field field, Object where, Object what)
            throws Exception {
        field.setAccessible(true);
        field.set(getRealInstanceForFieldInjection(where, field), getRealInstanceForFieldInjection(what, field));
        field.setAccessible(false);
    }

    private static void resolveMethodInjection(Class whatInject, Class whereInject)
            throws Exception {
        Object what = findFakeObject(whatInject);
        Object where = findFakeObject(whereInject);
    }

    private static Set<ClassToInject> getClassesWhichMustBeInjected(Class clazz, Map<Class, Integer> map) {
        Set<ClassToInject> toBeInjected = new HashSet<>();
        isCorrectInjection(clazz, findInjection(toBeInjected, clazz.getDeclaredFields(), map));
        isCorrectInjection(clazz, findInjection(toBeInjected, clazz.getDeclaredMethods(), map));
        return toBeInjected;
    }

    private static class ClassToInject {
        private InjectType injectType;
        private Class<?> clazz;

        ClassToInject(InjectType injectType, Class<?> clazz) {
            this.injectType = injectType;
            this.clazz = clazz;
        }
    }

    private enum InjectType {
        FIELD, METHOD
    }

    private static Class findInjection(Set<ClassToInject> classes,
                                       Field[] accessibleObjects,
                                       Map<Class, Integer> map) {
        for (Field accessibleObject : accessibleObjects) {
            if (accessibleObject.isAnnotationPresent(Inject.class)) {
                try {
                    accessibleObject.setAccessible(true);
                    Class type = accessibleObject.getType();
                    accessibleObject.setAccessible(false);
                    map.merge(type, 1, (k, v) -> v + 1);
                    boolean add = classes.add(new ClassToInject(InjectType.FIELD, type));
                    if (!add) {
                        return type;
                    }
                } catch (Exception e) {
                    throw new InjectionError(e);
                }
            }
        }
        return null;
    }

    private static Class findInjection(Set<ClassToInject> classes,
                                       Method[] accessibleObjects,
                                       Map<Class, Integer> map) {
        for (Method accessibleObject : accessibleObjects) {
            if (accessibleObject.isAnnotationPresent(Inject.class)) {
                try {
                    accessibleObject.setAccessible(true);
                    Class type = accessibleObject.getReturnType();
                    accessibleObject.setAccessible(false);
                    map.merge(type, 1, (k, v) -> v + 1);
                    boolean add = classes.add(new ClassToInject(InjectType.METHOD, type));
                    if (!add) {
                        return type;
                    }
                } catch (Exception e) {
                    throw new InjectionError(e);
                }
            }
        }
        return null;
    }


    private static void isCorrectInjection(Class currentClass, Class newOneClass) {
        if (newOneClass == null) {
            return;
        }
        if (currentClass == newOneClass) {
            throw new CyclicInjection("The same class cannot be injected!");
        }
        throw new TwiceInjection(newOneClass.getName() + " is already injected!");
    }


    private static Object getRealInstanceForFieldInjection(Object fakeInstance, Field where) throws Exception {
        if (fakeInstance instanceof Set) {
            // it's set of impl classes
            @SuppressWarnings("unchecked")
            Set<Class> mightBeImpls = (Set<Class>) fakeInstance;
            Class impl = where.getAnnotation(Inject.class).impl();
            if (Void.class == impl) {
                throw new InjectionError("Interface " + where.getName() + " has a lot of implementations " + mightBeImpls);
            } else {
                Class<?> realClassOfFutureInstance = mightBeImpls.stream()
                        .filter(impl::equals)
                        .findAny()
                        .orElseThrow(() -> new InjectionError("Cannot find implantation for " + impl));
                if (defaultConstructorFriendly(realClassOfFutureInstance)) {
                    return realClassOfFutureInstance.getConstructor().newInstance();
                } else {
                    throw new InjectionError("Could not find default constructor for field injection");
                }
            }
        } else {
            Class<?> realClass = (Class<?>) fakeInstance;
            if (defaultConstructorFriendly(realClass)) {
                return newInstance(realClass, INSTANCE_THROUGH_DEF_CONSTRUCTOR);
            } else {
                throw new InjectionError("Could not find default constructor for field injection");
            }
        }
    }

    private static Object newInstance(Class<?> clazz, Function<Class<?>, Object> maker) {
        try {
            Bean annotation = clazz.getAnnotation(Bean.class);
            if (annotation.scope() == Scope.SINGLETON) {
                Object instance = SINGLETON_HOLDER.get(clazz);
                if (instance == null) {
                    instance = maker.apply(clazz);
                }
                SINGLETON_HOLDER.put(clazz, instance);
                return instance;
            } else {
                return maker.apply(clazz);
            }
        } catch (Exception e) {
            throw new InjectionError("Could not create instance ", e);
        }
    }

    private static boolean defaultConstructorFriendly(Class clazz) {
        return Stream.of(clazz.getConstructors())
                .anyMatch((c) -> c.getParameterCount() == 0);
    }
}
