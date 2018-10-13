package com.github.tarcv.ztest.simulation;

import java.util.HashMap;
import java.util.Map;

import static com.github.tarcv.ztest.simulation.Constants.PROP_FROZEN;
import static com.github.tarcv.ztest.simulation.Constants.PROP_TOTALLYFROZEN;
import static com.github.tarcv.ztest.simulation.Symbols.PLAYERINFO_PLAYERCLASS;

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
                simulation.onPlayerJoined(pawn);
            } else if (pawn.getHealth() <= 0) {
                this.pawn = createPawn();
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

    boolean isBot() {
        return isBot;
    }

    int getInfo(int whichInfo) {
        switch (whichInfo) {
            case PLAYERINFO_PLAYERCLASS:
                return (int)getCVarInternal("playerclass");
            default:
                throw new UnsupportedOperationException("This whichInfo is not supported");
        }
    }

    int getPawnClass() {
        return pawn.getClassIndex();
    }

    void setCVar(String name, String newValue) {
        simulation.assertTickLockHeld(); {
            if (!simulation.getCVarType(name).isPlayerOwned()) throw new IllegalArgumentException("CVAR is not a user one");
            userCvarValues.put(name, newValue);
        }

    }
}
