package com.github.tarcv.ztest.simulation;

import com.sun.istack.internal.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class MapContext {
    private final List<Script> scripts = Collections.synchronizedList(new ArrayList<>());
    private final Set<Player> joinedPlayers = new HashSet<Player>();
    private final ScriptContext context;
    private final Constructor<? extends MapContext> ctor;
    private final Simulation simulation;
    private final MapContext rootContext;

    public MapContext(Simulation simulation, @Nullable MapContext rootContext) {
        try {
            this.ctor = this.getClass().getConstructor(Simulation.class, MapContext.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        this.simulation = simulation;
        this.context = new ScriptContext(simulation, null);
        this.rootContext = rootContext == null ? this : rootContext;
        simulation.registerScriptEventsListener(this);
    }

    // TODO: use annotations instead of this method
    protected void addScript(String name, String type) {
        scripts.add(new Script(name, ScriptType.valueOf(type)));
    }

    // TODO: use annotations instead of this method
    protected void addScript(String name) {
        addScript(name, ScriptType.CLOSED.name());
    }

    protected void ACS_NamedExecuteWait(String name, int where, Object... args) {
        if (where != 0) throw new IllegalArgumentException("where must be 0");
        // TODO
    }

    protected void print(String format, Object... args) {
        Thing activator = context.getActivator();
        if (activator instanceof PlayerPawn) {
            ((PlayerPawn)activator).printbold(format, args);
        } else {
            throw new AssertionError("HUD actions should executed by players only");
        }

    }
    protected int strcmp(String s1, String s2) {
        return s1.compareTo(s2);
    }


    protected void printbold(String format, Object... args) {

    }

    protected void SetPlayerProperty(int playerNumber, int value, int which) {

    }

    protected int checkInventory(String name) {
        return 0;
    }

    protected void ACS_NamedExecuteAlways(String qcde_duel_showDraft, int i) {
    }

    protected int playerIsSpectator(int playerNumber) {
        return 0;
    }

    protected int playerNumber() {
        return 0;
    }

    protected void delay(int tics) {

    }

    protected int getCVar(String name) {
        return 0;
    }
    protected int random(int min, int maxInclusive) {
        return 0;
    }

    protected void hudmessage(int type, int id, int color, double x, double y, double time, String format, Object... args) {

    }

    protected void setFont(String fontName) {

    }

    protected void setHudSize(int width, int height, boolean includeStatusBar) {

    }

    protected void terminate() {
        throw new TerminateScriptException();
    }

    protected boolean playerIsBot(int pn) {
        return false;
    }

    protected int playerCount() {
        return 0;
    }

    protected int getPlayerInfo(int playerNumber, int whichInfo) {
        return 0;
    }

    protected int timer() {
        return 0;
    }

    protected void giveInventory(String itemActivated, int count) {

    }

    protected int playerClass(int playerNumber) {
        return 0;
    }

    protected int playerHealth() {
        return 100;
    }

    protected void Thing_ChangeTID(int targetTid, int newTid) {

    }

    protected boolean isTidUsed(int mytid) {
        return false;
    }

    protected int activatorTID() {
        return 0;
    }

    protected void consoleCommand(String command) {

    }

    protected void setCVarString(String cvar, String newValue) {

    }

    protected void Thing_Damage(int tid, int damage, String mod) {

    }

    protected void setActivator(int newtid) {

    }

    protected void spawnForced(String type, double x, double y, int todo, int newtid, int todo2) {

    }

    protected int uniqueTID() {
        return 0;
    }
    protected void setActorVelocity(int velx, int vely, int velz, int todo, boolean todo1, boolean todo2) {

    }
    protected String getCVarString(String cvar) {
        return "";
    }

    protected int getActorProperty(int tid, int which) {
        return 0;
    }

    void onPlayerJoined(PlayerPawn playerPawn) {
        if (rootContext != this) {
            throw new IllegalStateException("Method can only be called on the original map context");
        }
        synchronized (scripts) {
            boolean firstTimeJoined = joinedPlayers.add(playerPawn.getPlayer());
            scripts.forEach(script -> {
                try {
                    if ((firstTimeJoined && script.type.contains(ScriptType.ENTER))
                     || (!firstTimeJoined && script.type.contains(ScriptType.RESPAWN))) {
                        MapContext mapContext = ctor.newInstance(simulation, rootContext);
                        mapContext.scheduleScriptOnThisContext(script.name);
                    }
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
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
        simulation.scheduleOnNextTic(() -> {
            try {
                scriptMethod.invoke(this, args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException(e);
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

    private enum ScriptType {
        CLOSED,
        OPEN,
        ENTER,
        DEATH,
        NET,
        EVENT,
        RESPAWN
    }
}
