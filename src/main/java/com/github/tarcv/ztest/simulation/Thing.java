package com.github.tarcv.ztest.simulation;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Consumer;

import static com.github.tarcv.ztest.simulation.Simulation.getConstructor;

public class Thing {
    private final Set<String> flags = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, Object> properties = Collections.synchronizedMap(new HashMap<>());
    private final List<State> stateList = Collections.synchronizedList(new ArrayList<>());
    private final List<CustomInventory> inventory = Collections.synchronizedList(new ArrayList<>());
    protected final Simulation simulation;
    private volatile Thing activator = this;
    private volatile int tid = 0;
    private volatile double x = 0;
    private volatile double y = 0;
    private volatile double z = 0;
    private volatile int angle = 0;
    private volatile double velx = 0;
    private volatile double vely = 0;
    private volatile double velz = 0;
    private volatile double alpha = 1.0;

    Thing(Simulation simulation) {
        simulation.registerThing(this);
        this.simulation = simulation;
    }

    Thing getActivator() {
        return activator;
    }

    protected final void addFlag(String flag) {
        flags.add(flag.toUpperCase());
    }

    protected final void removeFlag(String flag) {
        if (!flags.remove(flag.toUpperCase())) {
            throw new AssertionError("Flag " + flag + " was not present");
        }
    }

    final boolean hasFlag(String flag) {
        return flags.contains(flag.toUpperCase());
    }

    protected void setProperty(String name, Object value) {
        verifyPropertyNameAndValue(name, value);
        properties.put(name, value);
    }

    protected void verifyPropertyNameAndValue(String name, Object value) {
        throw new IllegalArgumentException("Unknown property " + name);
    }

    protected final void states(String sprite, String frames, int duration) {
        states(sprite, frames, duration, t -> {});
    }

    protected final void states(String sprite, String frames, int duration, Consumer<Thing> target) {
        target.accept(this);
        synchronized (stateList) {
            stateList.add(new State(sprite + frames, duration));
            if (stateList.size() > 100) {
                stateList.remove(0);
            }

            // TODO: delay(duration)

            target.accept(activator);
        }
    }

    void setActivator(Thing owner) {
        this.activator = owner;
    }

    public void A_Print(String message) {
        if (activator instanceof PlayerPawn) {
            ((PlayerPawn)activator).A_Print("%s", message);
        } else {
            throw new AssertionError("HUD actions should executed by players only");
        }
    }

    void pickItem(CustomInventory item) {
        inventory.add(item);
        item.pickupBy(this);
    }

    public void A_GiveInventory(String className, int count) {
        giveInventory(className, count);
    }

    void giveInventory(String className, int count) {
        try {
            Class<?> aClass = simulation.classForSimpleName(className);
            Constructor<?> constructor = getConstructor(aClass);
            for (int i = 0; i < count; i++) {
                CustomInventory item = (CustomInventory) constructor.newInstance(simulation);
                activator.pickItem(item);
            }
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | NoSuchMethodException
                | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public void A_TakeInventory(String className, int count) {
        takeInventory(className, count);
    }

    void takeInventory(String className, int count) {
        for (int i = 0; i < count; i++) {
            activator.removeItem(className);
        }
    }

    private void removeItem(String className) {
        synchronized (inventory) {
            Optional<CustomInventory> itemToRemove = inventory.stream()
                    .filter(item -> className.equals(item.getClass().getSimpleName()))
                    .findAny();
            itemToRemove.map(item -> {
                inventory.remove(item);
                return item;
            });
        }
    }

    public void A_ChangeFlag(String flag, int newValue) {
        if (newValue != 0) {
            addFlag(flag);
        } else if (hasFlag(flag)) {
            removeFlag(flag);
        }
    }

    public void A_ChangeVelocity(float velX, float velY, float velZ) {
        // TODO
    }

    public void A_StopSound(int channel) {
        // TODO
    }

    public void A_ClearSoundTarget() {
        // TODO
    }

    public void A_RadiusGive(String item, int radius, int who) {
        // TODO
    }

    public void A_SetTranslucent(double alpha) {
        // TODO
    }

    public void SetPlayerProperty(int who, int set, int which) {
        if (who != 0) throw new UnsupportedOperationException();
        if (activator instanceof PlayerPawn) {
            ((PlayerPawn)activator).SetPlayerProperty(which, set);
        } else {
            throw new AssertionError("Should executed by players only");
        }

    }

    int checkInventory(String name) {
        synchronized (inventory) {
            return (int) inventory.stream()
                    .filter(i -> i.getClass().getSimpleName().equalsIgnoreCase(name))
                    .count();
        }
    }

    int getTid() {
        return tid;
    }

    void setTid(int tid) {
        this.tid = tid;
    }

    void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    void setAngle(int angle) {
        this.angle = angle;
    }

    void setVelocity(double velx, double vely, double velz) {
        this.velx = velx;
        this.vely = vely;
        this.velz = velz;
    }

    protected Object getProperty(String name) {
        Object value = properties.get(name);
        if (value == null) {
            throw new IllegalStateException(String.format("Property %s was not set", name));
        }
        return value;
    }

    private static class State {
        private final String spriteFrame;
        private final int duration;

        private State(String spriteFrame, int duration) {
            this.spriteFrame = spriteFrame;
            this.duration = duration;
        }
    }
}
