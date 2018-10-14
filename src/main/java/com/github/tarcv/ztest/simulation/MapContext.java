    package com.github.tarcv.ztest.simulation;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.github.tarcv.ztest.simulation.MapContext.ScriptType.*;

public class MapContext {
    private final List<Script> scripts = Collections.synchronizedList(new ArrayList<>()); // TODO: make read-only
    private final Set<Player> joinedPlayers = new HashSet<>();
    private final ScriptContext context;
    private final MapContextCreator ctor;
    private final Simulation simulation;
    private final MapContext rootContext;
    private final List<String> executedScripts = Collections.synchronizedList(new ArrayList<>());

    protected interface MapContextCreator {
        MapContext create(Simulation simulation, MapContext context);
    }

    public MapContext(Simulation simulation, @Nullable MapContext rootContext, MapContextCreator supplier) {
        this.ctor = supplier;
        this.simulation = simulation;
        this.context = new ScriptContext(simulation, null);
        if (rootContext == null) {
            this.rootContext = this;
            this.simulation.registerScriptEventsListener(this);
        } else {
            this.rootContext = rootContext;
        }
    }

    // TODO: use annotations instead of this method
    protected void addScript(String name, ScriptType type) {
        scripts.add(new Script(name, type));
    }

    // TODO: use annotations instead of this method
    protected void addScript(String name) {
        addScript(name, CLOSED);
    }

    protected void ACS_NamedExecuteWait(String name, int where, Object... args) {
        if (where != 0) throw new IllegalArgumentException("where must be 0");
        scheduleScriptOnThisContext(name, args);
        ScriptWait(name);
    }

    protected void ScriptWait(String name) {
        delayUntil(context -> !context.executedScripts.contains(name));
    }

    protected void print(String format, Object... args) {
        Thing activator = context.getActivator();
        if (activator instanceof PlayerPawn) {
            ((PlayerPawn)activator).print(format, args);
        } else {
            throw new AssertionError("HUD actions should executed by players only");
        }

    }
    protected int strcmp(String s1, String s2) {
        return s1.compareTo(s2);
    }


    protected void printbold(String format, Object... args) {
        simulation.getPlayers().forEach(p -> p.printbold(format, args));
    }

    protected void setPlayerProperty(int who, int value, int which) {
        if (who == 1) {
            simulation.getPlayers().forEach(p -> p.setProperty(which, value));
        } else if (who == 0) {
            Thing activator = activatorInternal();
            if (activator instanceof PlayerPawn) {
                ((PlayerPawn) activator).getPlayer().setProperty(which, value);
            } else {
                throw new IllegalStateException("activatorInternal must be a player");
            }
        } else {
            throw new IllegalArgumentException("who should be 0 or 1");
        }
    }

    protected Thing activatorInternal() {
        return context.getActivator();
    }

    protected int checkInventory(String name) {
        return activatorInternal().checkInventory(name);
    }

    protected boolean ACS_NamedExecuteAlways(String name, int map, Object... args) {
        if (map != 0) throw new UnsupportedOperationException("Executing script on other map is not supported");
        if (args.length > 3) throw new IllegalArgumentException("At most 3 script argument can be set");
        scheduleScriptOnThisContext(name, args);
        return true;
    }

    protected int playerIsSpectator(int playerNumber) {
        boolean spectator = simulation
                .getPlayerByIndex(playerNumber)
                .isSpectator();
        return spectator ? 1 : 0;
    }

    protected int playerNumber() {
        Thing activator = activatorInternal();
        if (activator instanceof PlayerPawn) {
            return ((PlayerPawn) activator).getPlayer().getIndex();
        } else {
            throw new IllegalStateException("activatorInternal must be a player");
        }
    }

    protected void delay(int tics) {
        if (tics <= 0) throw new IllegalArgumentException("tics number must be positive");
        int targetTic = simulation.getCurrentTic() + tics;
        delayUntil(context -> simulation.getCurrentTic() >= targetTic);
    }

    private void delayUntil(final Predicate<MapContext> predicate) {
        synchronized (this) { // TODO: replace this lock if needed
            Simulation.getThreadContext().setDelayed(true);
            simulation.scheduleOnNextTic(new Runnable() {
                @Override
                public void run() {
                    if (predicate.test(MapContext.this)) {
                        synchronized (MapContext.this) {
                            MapContext.this.notify();
                        }
                    } else {
                        simulation.scheduleOnNextTic(this);
                    }
                }
            });
            while (!predicate.test(MapContext.this)) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            Simulation.getThreadContext().setDelayed(false);
        }
    }

    protected int getCVar(String name) {
        return (int) getCVarInternal(name);
    }

    private Object getCVarInternal(String name) {
        Simulation.CVarTypes cVarType = simulation.getCVarType(name);
        if (!cVarType.isPlayerOwned()) {
            return simulation.getCVarInternal(name);
        } else {
            Thing activator = activatorInternal();
            if (activator instanceof PlayerPawn) {
                return ((PlayerPawn) activator).getPlayer().getCVarInternal(name);
            } else {
                throw new IllegalStateException("activatorInternal must be a player");
            }
        }
    }

    protected int random(int min, int maxInclusive) {
        return simulation.random(min, maxInclusive);
    }

    protected void hudmessage(int type, int id, int color, double x, double y, double time, String format, Object... args) {
        // TODO
    }

    protected void setFont(String fontName) {
        // TODO
    }

    protected void setHudSize(int width, int height, boolean includeStatusBar) {
        // TODO
    }

    protected void terminate() {
        throw new TerminateScriptException();
    }

    protected boolean playerIsBot(int pn) {
        Thing activator = activatorInternal();
        if (activator instanceof PlayerPawn) {
            return ((PlayerPawn) activator).getPlayer().isBot();
        } else {
            throw new IllegalStateException("activatorInternal must be a player");
        }
    }

    protected int playerCount() {
        return simulation.getPlayers().size();
    }

    protected int getPlayerInfo(int playerNumber, int whichInfo) {
        return simulation.getPlayerByIndex(playerNumber).getInfo(whichInfo);
    }

    protected int timer() {
        return simulation.getCurrentTic();
    }

    protected void giveInventory(String item, int count) {
        activatorInternal().A_GiveInventory(item, count);
    }

    protected int playerClass(int playerNumber) {
        return simulation.getPlayerByIndex(playerNumber).getPawnClass();
    }

    protected int playerHealth() {
        return ((Actor) activatorInternal()).getHealth();
    }

    protected void Thing_ChangeTID(int targetTid, int newTid) {
        simulation
                .assertedGetThingsByTid(targetTid, activatorInternal())
                .forEach(t -> t.setTid(newTid));
    }

    protected boolean isTidUsed(int tid) {
        if (tid == 0) throw new IllegalArgumentException("tid should not be 0");
        return !simulation
                .assertedGetThingsByTid(tid, null)
                .isEmpty();
    }

    protected int activatorTID() {
        return activatorInternal().getTid();
    }

    protected void consoleCommand(String command) {
        throw new UnsupportedOperationException("Override this method in your test");
    }

    protected boolean setCVarString(String name, String newValue) {
        setCVarInternal(name, newValue, false);
        return true;
    }

    protected void setCVarAsConsole(String name, Object newValue) {
        setCVarInternal(name, newValue, true);
    }

    private void setCVarInternal(String name, Object newValue, boolean consoleAccess) {
        Simulation.CVarTypes cVarType = simulation.getCVarType(name);
        if (!cVarType.isModifiableFromScripts() && !consoleAccess) {
            throw new IllegalArgumentException("CVar is not modifiable from ACS or DECORATE");
        }

        if (!cVarType.isPlayerOwned()) {
            simulation.setCVar(name, newValue);
        } else {
            Thing activator = activatorInternal();
            if (activator instanceof PlayerPawn) {
                ((PlayerPawn) activator).getPlayer().setCVar(name, newValue);
            } else {
                throw new IllegalStateException("activatorInternal must be a player");
            }
        }
    }

    protected void Thing_Damage2(int tid, int damage, String mod) {
        DamageType damageType;
        try {
            damageType = (DamageType) simulation.classForSimpleName(mod).newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
        simulation.assertedGetThingsByTid(tid, activatorInternal()).forEach(
                t -> ((Actor)t).damageThing(damage, damageType, activatorInternal()));
    }

    protected void setActivator(int newtid) {
        if (newtid == 0) throw new IllegalArgumentException("newtid must not be 0");
        List<Thing> things = simulation.assertedGetThingsByTid(newtid, null);
        if (things.size() != 1) throw new IllegalStateException("there should be exactly one thing with the given tid");
        context.setActivator(things.get(0));
    }

    protected int spawnForced(String type, double x, double y, int z, int newtid, int angle) {
        simulation.createThing(type, x, y, z, newtid, angle);
        return 1;
    }

    protected int uniqueTID() {
        return simulation.createUniqueTid();
    }
    protected void setActorVelocity(int tid, double velx, double vely, double velz, boolean add, boolean setbob) {
        if (add) throw new UnsupportedOperationException("add is not supported");
        if (setbob) throw new UnsupportedOperationException("setbob is not supported");
        simulation.assertedGetThingsByTid(tid, activatorInternal()).forEach(t -> t.setVelocity(velx, vely, velz));
    }
    protected String getCVarString(String name) {
        return (String) getCVarInternal(name);
    }

    protected int getActorProperty(int tid, int which) {
        List<Thing> things = simulation.assertedGetThingsByTid(tid, activatorInternal());
        if (things.size() != 0) throw new IllegalStateException("Only one thing should be found by given tid");
        return 0;
    }

    void onPlayerJoined(PlayerPawn player) {
        if (rootContext != this) {
            throw new IllegalStateException("Method can only be called on the original map context");
        }
        scheduleScriptsByType(ENTER, player);
    }

    private void scheduleScriptsByType(ScriptType type, Thing activator) {
        synchronized (scripts) {
            scripts.forEach(script -> {
                if (script.type.contains(type)) {
                    MapContext mapContext = ctor.create(simulation, rootContext);
                    mapContext.context.setActivator(activator);
                    mapContext.scheduleScriptOnThisContext(script.name);
                }
            });
        }
    }

    void onPlayerRespawned(PlayerPawn player) {
        if (rootContext != this) {
            throw new IllegalStateException("Method can only be called on the original map context");
        }
        scheduleScriptsByType(RESPAWN, player);
    }

    void onPlayerKilled(PlayerPawn player) {
        if (rootContext != this) {
            throw new IllegalStateException("Method can only be called on the original map context");
        }
        scheduleScriptsByType(DEATH, player);
        // TODO: implement calling fragged EVENT script call
    }

    void onMapLoaded() {
        if (rootContext != this) {
            throw new IllegalStateException("Method can only be called on the original map context");
        }
        scheduleScriptsByType(OPEN, null);
    }

    List<Runnable> createInitRunnables() {
        synchronized (scripts) {
            return scripts.stream()
                    .filter(script -> script.type.contains(OPEN))
                    .map(script -> getScriptRunnable(script.name, new Object[0]))
                    .collect(Collectors.toList());
        }
    }

    private void scheduleScriptOnThisContext(String name, Object... args) {
        Runnable scriptRunnable = getScriptRunnable(name, args);
        simulation.scheduleOnNextTic(scriptRunnable);
    }

    private Runnable getScriptRunnable(String name, Object[] args) {
        Method scriptMethod = findScriptMethodByName(name);
        MapContext that = this;
        return () -> {
            try {
                boolean added = executedScripts.add(name);
                assert added;
                scriptMethod.setAccessible(true);
                scriptMethod.invoke(that, args);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            } catch (InvocationTargetException e) {
                Throwable targetException = e.getTargetException();
                if (targetException instanceof TerminateScriptException) {
                    // workaround to emulate `terminate` statement
                } else {
                    throw new IllegalStateException(e);
                }
            } finally {
                boolean removed = executedScripts.remove(name);
                assert removed;
            }
        };
    }

    private Method findScriptMethodByName(String name) {
        for (Method method : getClass().getDeclaredMethods()) {
            if (name.equals(method.getName())) {
                return method;
            }
        }
        throw new IllegalStateException("Script '" + name + "' not found");
    }

    private class Script {
        final String name;
        final Set<ScriptType> type;

        private Script(String name, ScriptType... types) {
            this.name = name;
            this.type = EnumSet.copyOf(Arrays.asList(types));
        }
    }

    public enum ScriptType {
        CLOSED,
        OPEN,
        ENTER,
        DEATH,
        NET,
        EVENT,
        RESPAWN,
        DISCONNECT
    }
}
