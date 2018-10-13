package com.github.tarcv.ztest.simulation;

import com.sun.istack.internal.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static com.github.tarcv.ztest.simulation.MapContext.ScriptType.*;

public class MapContext {
    private final List<Script> scripts = Collections.synchronizedList(new ArrayList<>());
    private final Set<Player> joinedPlayers = new HashSet<>();
    private final ScriptContext context;
    private final MapContextCreator ctor;
    private final Simulation simulation;
    private final MapContext rootContext;
    private ThreadLocal<Simulation.DelayableContext> delayableContext = new ThreadLocal<>();

    protected interface MapContextCreator {
        MapContext create(Simulation simulation, MapContext context);
    }

    public MapContext(Simulation simulation, @Nullable MapContext rootContext, MapContextCreator supplier) {
        this.ctor = supplier;
        this.simulation = simulation;
        this.context = new ScriptContext(simulation, null);
        this.rootContext = rootContext == null ? this : rootContext;
        this.simulation.registerScriptEventsListener(this);
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
        // TODO
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
            Thing activator = activator();
            if (activator instanceof PlayerPawn) {
                ((PlayerPawn) activator).getPlayer().setProperty(which, value);
            } else {
                throw new IllegalStateException("activator must be a player");
            }
        } else {
            throw new IllegalArgumentException("who should be 0 or 1");
        }
    }

    private Thing activator() {
        return context.getActivator();
    }

    protected int checkInventory(String name) {
        return activator().checkInventory(name);
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
        Thing activator = activator();
        if (activator instanceof PlayerPawn) {
            return ((PlayerPawn) activator).getPlayer().getIndex();
        } else {
            throw new IllegalStateException("activator must be a player");
        }
    }

    protected void delay(int tics) {
        if (tics <= 0) throw new IllegalArgumentException("tics number must be positive");
        synchronized (this) {
            delayableContext.get().setDelayed(true);
            int targetTic = simulation.getCurrentTic();
            while (simulation.getCurrentTic() < targetTic) {
                MapContext that = this;
                simulation.scheduleOnNextTic(new Simulation.DelayableRunnable() {
                    @Override
                    public void run() {
                        if (simulation.getCurrentTic() >= targetTic) {
                            that.notify();
                        }
                    }
                });
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            delayableContext.get().setDelayed(false);
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
            Thing activator = activator();
            if (activator instanceof PlayerPawn) {
                return ((PlayerPawn) activator).getPlayer().getCVarInternal(name);
            } else {
                throw new IllegalStateException("activator must be a player");
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
        Thing activator = activator();
        if (activator instanceof PlayerPawn) {
            return ((PlayerPawn) activator).getPlayer().isBot();
        } else {
            throw new IllegalStateException("activator must be a player");
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
        activator().A_GiveInventory(item, count);
    }

    protected int playerClass(int playerNumber) {
        return simulation.getPlayerByIndex(playerNumber).getPawnClass();
    }

    protected int playerHealth() {
        return ((Actor)activator()).getHealth();
    }

    protected void Thing_ChangeTID(int targetTid, int newTid) {
        simulation
                .assertedGetThingsByTid(targetTid, activator())
                .forEach(t -> t.setTid(newTid));
    }

    protected boolean isTidUsed(int tid) {
        if (tid == 0) throw new IllegalArgumentException("tid should not be 0");
        return !simulation
                .assertedGetThingsByTid(tid, null)
                .isEmpty();
    }

    protected int activatorTID() {
        return activator().getTid();
    }

    protected void consoleCommand(String command) {
        Thing activator = activator();
        if (activator instanceof PlayerPawn) {
            throw new UnsupportedOperationException("TODO");
        } else {
            throw new UnsupportedOperationException("Only player as an activator is supported");
        }

    }

    protected boolean setCVarString(String name, String newValue) {
        Simulation.CVarTypes cVarType = simulation.getCVarType(name);
        if (!cVarType.isModifiableFromScripts()) {
            throw new IllegalArgumentException("CVar is not modifiable from ACS or DECORATE");
        }

        if (!cVarType.isPlayerOwned()) {
            simulation.setCVar(name, newValue);
        } else {
            Thing activator = activator();
            if (activator instanceof PlayerPawn) {
                ((PlayerPawn) activator).getPlayer().setCVar(name, newValue);
            } else {
                throw new IllegalStateException("activator must be a player");
            }
        }
        return true;
    }

    protected void Thing_Damage2(int tid, int damage, String mod) {
        DamageType damageType;
        try {
            damageType = (DamageType) Class.forName(mod).newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
        simulation.assertedGetThingsByTid(tid, activator()).forEach(
                t -> ((Actor)t).damageThing(damage, damageType, activator()));
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
        simulation.assertedGetThingsByTid(tid, activator()).forEach(t -> t.setVelocity(velx, vely, velz));
    }
    protected String getCVarString(String name) {
        return (String) getCVarInternal(name);
    }

    protected int getActorProperty(int tid, int which) {
        List<Thing> things = simulation.assertedGetThingsByTid(tid, activator());
        if (things.size() != 0) throw new IllegalStateException("Only one thing should be found by given tid");
        return 0;
    }

    void onPlayerJoined(PlayerPawn player) {
        if (rootContext != this) {
            throw new IllegalStateException("Method can only be called on the original map context");
        }
        schedulScriptsByType(ENTER, player);
    }

    private void schedulScriptsByType(ScriptType enter, Thing activator) {
        synchronized (scripts) {
            scripts.forEach(script -> {
                if (script.type.contains(enter)) {
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
        schedulScriptsByType(RESPAWN, player);
    }

    void onPlayerKilled(PlayerPawn player) {
        if (rootContext != this) {
            throw new IllegalStateException("Method can only be called on the original map context");
        }
        schedulScriptsByType(DEATH, player);
        // TODO: implement calling fragged EVENT script call
    }

    void onMapLoaded() {
        if (rootContext != this) {
            throw new IllegalStateException("Method can only be called on the original map context");
        }
        synchronized (scripts) {
            scripts.forEach(script -> {
                if (script.type.contains(ScriptType.OPEN)) {
                    scheduleScriptOnThisContext(script.name);
                }
            });
        }
    }

    private void scheduleScriptOnThisContext(String name, Object... args) {
        Method scriptMethod = findScriptMethodByName(name);
        simulation.scheduleOnNextTic(new Simulation.DelayableRunnable() {
            @Override
            public void run() {
                try {
                    scriptMethod.invoke(this, args);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
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
