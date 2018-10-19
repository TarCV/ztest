package com.github.tarcv.ztest.simulation;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tarcv.ztest.simulation.AcsConstants.PLAYERINFO_PLAYERCLASS;
import static com.github.tarcv.ztest.simulation.DecorateConstants.PROP_FROZEN;
import static com.github.tarcv.ztest.simulation.DecorateConstants.PROP_TOTALLYFROZEN;

public class Player implements Owner {
    private final Hud console = new Hud();
    private final String name;
    private final Simulation simulation;
    private final int initialHealth;
    private final int initialArmor;
    private final boolean isBot;
    private PlayerPawn pawn;
    private boolean frozen = false;
    private boolean totallyFrozen = false;
    private final Map<String, Object> userCvarValues = new HashMap<>();
    private int buttonsDown = 0;

    Player(Simulation simulation, String name, int initialHealth, int initialArmor, boolean isBot) {
        this.simulation = simulation;
        this.name = name;
        this.initialHealth = initialHealth;
        this.initialArmor = initialArmor;
        this.isBot = isBot;

        userCvarValues.put("playerclass", 0);
    }

    void A_Print(String format, Object[] arguments) {
        Hud.print(format, arguments);
    }

    void printbold(String format, Object[] args) {
        Hud.print(format, args);
    }

    public void joinGame() {
        simulation.withTickLock(() -> {
            if (pawn == null) {
                this.pawn = createPawn();
                simulation.printfMarked("- %s joined the game as %d%n", name, this.pawn.getClassIndex());
                simulation.onPlayerJoined(pawn);
            } else if (pawn.getHealth() <= 0) {
                this.pawn = createPawn();
                simulation.printfMarked("- %s respawned as %d%n", name, this.pawn.getClassIndex());
                simulation.onPlayerRespawned(pawn);
            }
        });
    }

    private PlayerPawn createPawn() {
        return new PlayerPawn(simulation, this, (int)getCVarInternal("playerclass"), initialHealth, initialArmor);
    }

    void setProperty(int which, int value) {
        simulation.assertTickLockHeld(); {
            switch (which) {
                case PROP_FROZEN:
                    frozen = value != 0;
                    break;
                case PROP_TOTALLYFROZEN:
                    totallyFrozen = value != 0;
                    break;
                default:
                    throw new UnsupportedOperationException("property is not supported");
            }
        }
    }

    boolean isSpectator() {
        simulation.assertTickLockHeld(); {
            return this.pawn == null;
        }
    }

    int getIndex() {
        return simulation.getPlayerIndex(this);
    }

    Object getCVarInternal(String name) {
        simulation.assertTickLockHeld(); {
            if (!simulation.getCVarType(name).isPlayerOwned()) throw new IllegalArgumentException("CVAR is not a user one");
            return userCvarValues.get(name);
        }
    }

    public boolean isBot() {
        AtomicBoolean result = new AtomicBoolean();
        simulation.withTickLock(() -> result.set(isBot));
        return result.get();
    }

    int getInfo(int whichInfo) {
        switch (whichInfo) {
            case PLAYERINFO_PLAYERCLASS:
                return (int)getCVarInternal("playerclass");
            default:
                throw new UnsupportedOperationException("This whichInfo is not supported");
        }
    }

    public int getPawnClass() {
        return pawn.getClassIndex();
    }

    public void setCVar(String name, Object newValue) {
        simulation.withTickLock(() -> {
            if (!simulation.getCVarType(name).isPlayerOwned()) throw new IllegalArgumentException("CVAR is not a user one");
            userCvarValues.put(name, newValue);
        });
    }

    public int getHealth() {
        AtomicInteger health = new AtomicInteger();
        simulation.withTickLock(() -> health.set(pawn.getHealth()));
        return health.get();
    }

    public void downKey(int key) {
        simulation.withTickLock(() -> buttonsDown = buttonsDown | key);
    }

    public void upKey(int key) {
        simulation.withTickLock(() -> buttonsDown = buttonsDown & (~key));
    }

    int getButtonState() {
        return buttonsDown;
    }

    PlayerPawn getPawn() {
        return pawn;
    }

    public void fragOther(Player victim) {
        simulation.withTickLock(() -> {
            victim.pawn.damageThing(999, new DamageFrag(simulation), pawn);
        });
    }
}
