package com.github.tarcv.ztest.simulation;

import java.util.*;
import java.util.function.Consumer;

public class Thing {
    private final Set<String> flags = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, Object> properties = Collections.synchronizedMap(new HashMap<>());
    private final List<State> stateList = Collections.synchronizedList(new ArrayList<>());
    private final List<CustomInventory> inventory = Collections.synchronizedList(new ArrayList<>());
    private volatile Thing activator = this;

    public Thing(Simulation simulation) {
        simulation.registerThing(this);
    }

    protected Thing getActivator() {
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

    public final boolean hasFlag(String flag) {
        return flags.contains(flag.toUpperCase());
    }

    protected final void setProperty(String name, Object value) {
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

    protected void setActivator(Thing owner) {
        this.activator = owner;
    }

    public void A_Print(String message) {
        if (activator instanceof PlayerPawn) {
            ((PlayerPawn)activator).A_Print("%s", message);
        } else {
            throw new AssertionError("HUD actions should executed by players only");
        }
    }

    protected void pickItem(CustomInventory item) {
        inventory.add(item);
        item.pickupBy(this);
    }

    public void A_GiveInventory(String className, int count) {
        try {
            Class<?> aClass = Class.forName(className);
            for (int i = 0; i < count; i++) {
                CustomInventory item = (CustomInventory) aClass.newInstance();
                activator.pickItem(item);
            }
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void A_TakeInventory(String className, int count) {
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

    }

    public void SetPlayerProperty(int who, int set, int which) {
        if (who != 0) throw new UnsupportedOperationException();
        if (activator instanceof PlayerPawn) {
            ((PlayerPawn)activator).SetPlayerProperty(which, set);
        } else {
            throw new AssertionError("Should executed by players only");
        }

    }

    public static class State {
        private final String spriteFrame;
        private final int duration;

        private State(String spriteFrame, int duration) {
            this.spriteFrame = spriteFrame;
            this.duration = duration;
        }
    }
}
