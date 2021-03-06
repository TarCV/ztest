package com.github.tarcv.ztest.simulation;

import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.SuspendableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static com.github.tarcv.ztest.simulation.AcsConstants.APROP_HEALTH;
import static com.github.tarcv.ztest.simulation.AcsConstants.INPUT_BUTTONS;
import static com.github.tarcv.ztest.simulation.ScriptContext.ScriptType.*;

public abstract class ScriptContext<T extends ScriptContext> {
    @NotNull private final MapContext<T> mapContext;
    private final ActivatorHolder context = new ActivatorHolder(null);
    private final boolean isMainScriptContext;

    protected interface ScriptContextCreator<T extends ScriptContext> {
        ScriptContext<T> create(Simulation<T> simulation, MapContext<T> context);
    }

    public ScriptContext(
            Simulation<T> simulation,
            @Nullable MapContext<T> mapContext,
            ScriptContextCreator<T> supplier,
            List<Script<T>> scripts) {
        if (mapContext == null) {
            this.isMainScriptContext = true;
            this.mapContext = new MapContext<T>(simulation, supplier, scripts);
        } else {
            this.isMainScriptContext = false;
            this.mapContext = mapContext;
        }
    }

    protected void ACS_NamedExecuteWait(String name, int where, Object... args) throws SuspendExecution {
        if (where != 0) throw new IllegalArgumentException("where must be 0");
        ACS_NamedExecute(name, where, args);
        namedScriptWait(name);
    }

    protected void namedScriptWait(String name) throws SuspendExecution {
        delayUntil(() -> !mapContext.executedScripts.contains(name));
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


    protected void printBold(String format, Object... args) {
        mapContext.simulation.getPlayers().forEach(p -> p.printbold(format, args));
    }

    protected void SetPlayerProperty(int who, int value, int which) {
        if (who == 1) {
            mapContext.simulation.getPlayers().forEach(p -> p.setProperty(which, value));
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

    protected int playerIsSpectator(int playerNumber) {
        boolean spectator = mapContext.simulation
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

    protected void delay(int tics) throws SuspendExecution {
        if (tics <= 0) throw new IllegalArgumentException("tics number must be positive");
        int targetTic = mapContext.simulation.getCurrentTick() + tics;
        delayUntil(() -> mapContext.simulation.getCurrentTick() >= targetTic);
    }

    private void delayUntil(BooleanSupplier predicate) throws SuspendExecution {
        mapContext.simulation.getThreadContext()
                .delayUntil(predicate);
    }

    protected int getCVar(String name) {
        return (int) getCVarInternal(name);
    }

    private Object getCVarInternal(String name) {
        Simulation.CVarTypes cVarType = mapContext.simulation.getCVarType(name);
        if (!cVarType.isPlayerOwned()) {
            return mapContext.simulation.getCVarInternal(name);
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
        return mapContext.simulation.random(min, maxInclusive);
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
        return mapContext.simulation.getPlayers().size();
    }

    protected int getPlayerInfo(int playerNumber, int whichInfo) {
        return mapContext.simulation.getPlayerByIndex(playerNumber).getInfo(whichInfo);
    }

    protected int timer() {
        return mapContext.simulation.getCurrentTick();
    }

    protected void giveInventory(String item, int count) {
        activatorInternal().A_GiveInventory(item, count);
    }

    protected int playerClass(int playerNumber) {
        return mapContext.simulation.getPlayerByIndex(playerNumber).getPawnClass();
    }

    protected int playerHealth() {
        return ((Actor) activatorInternal()).getHealth();
    }

    protected void Thing_ChangeTID(int targetTid, int newTid) {
        mapContext.simulation
                .assertedGetThingsByTid(targetTid, activatorInternal())
                .forEach(t -> t.setTid(newTid));
    }

    protected boolean isTidUsed(int tid) {
        if (tid == 0) throw new IllegalArgumentException("tid should not be 0");
        return !mapContext.simulation
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
        Simulation.CVarTypes cVarType = mapContext.simulation.getCVarType(name);
        if (!cVarType.isModifiableFromScripts() && !consoleAccess) {
            throw new IllegalArgumentException("CVar is not modifiable from ACS or DECORATE");
        }

        if (!cVarType.isPlayerOwned()) {
            mapContext.simulation.setCVar(name, newValue);
        } else {
            Thing activator = activatorInternal();
            if (activator instanceof PlayerPawn) {
                ((PlayerPawn) activator).getPlayer().setCVar(name, newValue);
            } else {
                throw new IllegalStateException("activatorInternal must be a player");
            }
        }
    }

    protected void thing_Damage2(int tid, int damage, String mod) {
        DamageType damageType;
        try {
            damageType = (DamageType) mapContext.simulation.classForSimpleName(mod).newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
        mapContext.simulation.assertedGetThingsByTid(tid, activatorInternal()).forEach(
                t -> ((Actor)t).damageThing(damage, damageType, activatorInternal()));
    }

    protected void setActivator(int newtid) {
        if (newtid == 0) throw new IllegalArgumentException("newtid must not be 0");
        List<Thing> things = mapContext.simulation.assertedGetThingsByTid(newtid, null);
        if (things.size() != 1) throw new IllegalStateException("there should be exactly one thing with the given tid");
        context.setActivator(things.get(0));
    }

    protected int spawnForced(String type, double x, double y, int z, int newtid, int angle) {
        mapContext.simulation.createThing(type, x, y, z, newtid, angle);
        return 1;
    }

    protected int uniqueTID() {
        return mapContext.simulation.createUniqueTid();
    }
    protected void setActorVelocity(int tid, double velx, double vely, double velz, boolean add, boolean setbob) {
        if (add) throw new UnsupportedOperationException("add is not supported");
        if (setbob) throw new UnsupportedOperationException("setbob is not supported");
        mapContext.simulation.assertedGetThingsByTid(tid, activatorInternal()).forEach(t -> t.setVelocity(velx, vely, velz));
    }
    protected String getCVarString(String name) {
        return (String) getCVarInternal(name);
    }

    protected int getActorProperty(int tid, int which) {
        List<Thing> things = mapContext.simulation.assertedGetThingsByTid(tid, activatorInternal());
        if (things.size() != 1) throw new IllegalStateException("Only one thing should be found by given tid");
        switch (which) {
            case APROP_HEALTH:
                return ((Actor)things.get(0)).getHealth();
            default:
                throw new UnsupportedOperationException("Property is not supported");
        }
    }

    protected int getPlayerInput(int playerNumber, int type) {
        if (type != INPUT_BUTTONS) throw new UnsupportedOperationException("Only INPUT_BUTTONS is supported");
        return mapContext.simulation.getPlayerByIndex(playerNumber).getButtonState();
    }



    void onPlayerJoined(PlayerPawn player) {
        assertIsMainScriptContext();
        scheduleScriptsByType(ENTER, player);
    }

    void assertIsMainScriptContext() {
        if (!this.isMainScriptContext) {
            throw new IllegalStateException("Method can only be called on the original map context");
        }
    }

    private void scheduleScriptsByType(ScriptType type, Thing activator) {
        synchronized (mapContext.scripts) {
            mapContext.scripts.forEach(script -> {
                if (script.type.contains(type)) {
                    scheduleScriptInternal(activator, script, true, new Object[0]);
                }
            });
        }
    }

    private void scheduleScriptInternal(Thing activator, String name, boolean always, Object[] args) {
        Script<T> script = getScriptForName(name);
        scheduleScriptInternal(activator, script, always, args);
    }

    private ScriptContext.Script<T> getScriptForName(String name) {
        return mapContext.scripts.stream()
                    .filter(s -> s.name.equals(name))
                    .findAny()
                    .orElseGet(() -> {
                        throw new IllegalArgumentException("Script " + name + " not found");
                    });
    }

    private void scheduleScriptInternal(Thing activator, Script<T> script, boolean always, Object[] args) {
        ScriptContext<T> scriptContext = mapContext.ctor.create(mapContext.simulation, mapContext);
        scriptContext.context.setActivator(activator);
        scriptContext.scheduleScriptOnThisContext(script, always, args);
    }

    protected void ACS_NamedExecute(String name, int where, Object... args) {
        if (where != 0) throw new UnsupportedOperationException("Executing on another map is not supported");
        scheduleScriptInternal(activatorInternal(), name, false, args);
    }

    protected boolean ACS_NamedExecuteAlways(String name, int map, Object... args) {
        if (map != 0) throw new UnsupportedOperationException("Executing script on other map is not supported");
        if (args.length > 3) throw new IllegalArgumentException("At most 3 script argument can be set");
        scheduleScriptInternal(activatorInternal(), name, true, args);
        return true;
    }

    public void pukeScript(Player activator, String name, Object... args) {
        PlayerPawn pawn = activator.getPawn();
        pukeScript(pawn, name, args);
    }

    protected void printlnMarked(String s) {
        mapContext.simulation.printlnMarked(s);
    }

    public void pukeScript(Thing activator, String name, Object... args) {
        mapContext.simulation.withTickLock(() -> scheduleScriptInternal(activator, name, true, args));
    }

    void onPlayerRespawned(PlayerPawn player) {
        assertIsMainScriptContext();
        scheduleScriptsByType(RESPAWN, player);
    }

    void onPlayerKilled(PlayerPawn player) {
        assertIsMainScriptContext();
        scheduleScriptsByType(DEATH, player);
        // TODO: implement calling fragged EVENT script call
    }

    List<NamedRunnable> createInitRunnables() {
        synchronized (mapContext.scripts) {
            List<Script<T>> openScripts = mapContext.scripts.stream()
                    .filter(script -> script.type.contains(OPEN))
                    .collect(Collectors.toList());
            List<String> openScriptsNames = openScripts.stream()
                    .map(script -> script.name)
                    .collect(Collectors.toList());
            mapContext.executedScripts.addAll(openScriptsNames);
            return openScripts.stream()
                    .map(script -> getScriptRunnable(script, new Object[0]))
                    .collect(Collectors.toList());
        }
    }

    private void scheduleScriptOnThisContext(Script<T> script, boolean always, Object... args) {
        if (always || !mapContext.executedScripts.contains(script.name)) {
            boolean added = mapContext.executedScripts.add(script.name);
            assert added;
            // name is removed by runnabled returned from getScriptRunnable

            NamedRunnable scriptRunnable = getScriptRunnable(script, args);
            mapContext.simulation.scheduleOnNextTic(scriptRunnable);
        }
    }

    protected abstract NamedRunnable getScriptRunnable(Script<T> script, Object[] args);

    protected NamedRunnable createScriptRunnable(T that, Script<T> script, Object[] args) {
        return new NamedRunnable() {
            @Override
            public String name() {
                return script.name;
            }

            @Override
            public void run() throws SuspendExecution {
                try {
                    assert mapContext.executedScripts.contains(script.name);

                    script.runnable.callScript(that, args);
                    boolean removed = mapContext.executedScripts.remove(script.name);
                    assert removed;
                } catch (RuntimeException e) {
                    boolean removed = mapContext.executedScripts.remove(script.name);
                    assert removed;
                }
            }
        };
    }

    public static class Script<T extends ScriptContext> {
        final String name;
        final int argumentNumber;
        final ScriptRunnable<T> runnable;
        final Set<ScriptType> type;

        public Script(String name, int argumentNumber, ScriptRunnable<T> runnable, ScriptType... types) {
            this.name = name;
            this.argumentNumber = argumentNumber;
            this.runnable = runnable;
            this.type = types.length > 0 ? EnumSet.copyOf(Arrays.asList(types)) : EnumSet.noneOf(ScriptType.class);
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

    protected interface NamedRunnable extends SuspendableRunnable {
        String name();
    }
}

