package enchantmentmod.items;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.function.Supplier;
import java.util.Collection;
import necesse.engine.localization.Localization;
import necesse.engine.network.PacketReader;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.sound.SoundEffect;
import necesse.engine.sound.SoundManager;
import necesse.engine.util.ComparableSequence;
import necesse.engine.util.GameBlackboard;
import necesse.engine.util.GameMath;
import necesse.engine.util.GameUtils;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.GameResources;
import necesse.gfx.drawOptions.itemAttack.ItemAttackDrawOptions;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryAddConsumer;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryItemsRemoved;
import necesse.inventory.PlayerInventorySlot;
import necesse.inventory.container.Container;
import necesse.inventory.container.ContainerActionResult;
import necesse.inventory.container.slots.ContainerSlot;
import necesse.inventory.item.Item;
import necesse.inventory.recipe.Ingredient;
import necesse.inventory.recipe.IngredientCounter;
import necesse.level.maps.Level;

public class ShardPouch extends Item {
    public HashSet<String> combinePurposes = new HashSet();

    public ShardPouch() {
        super(1);
        this.setItemCategory(new String[]{"misc", "pouches"});
        this.rarity = Rarity.RARE;
        this.combinePurposes.add("leftclick");
        this.combinePurposes.add("leftclickinv");
        this.combinePurposes.add("rightclick");
        this.combinePurposes.add("lootall");
        this.combinePurposes.add("pouchtransfer");
        this.worldDrawSize = 32;
    }

    public ListGameTooltips getTooltips(InventoryItem item, PlayerMob perspective, GameBlackboard blackboard) {
        ListGameTooltips tooltips = super.getTooltips(item, perspective, blackboard);
        tooltips.add(Localization.translate("itemtooltip", "shardpouchstored", "shards", GameUtils.metricNumber((long)this.getCurrentShards(item))));
        return tooltips;
    }

    public void setDrawAttackRotation(InventoryItem item, ItemAttackDrawOptions drawOptions, float attackDirX, float attackDirY, float attackProgress) {
        drawOptions.swingRotation(attackProgress);
    }

    public InventoryItem onAttack(Level level, int x, int y, PlayerMob player, int attackHeight, InventoryItem item, PlayerInventorySlot slot, int animAttack, int seed, PacketReader contentReader) {
        int currentShards = this.getCurrentShards(item);
        int thrown = Math.min(1000, currentShards);
        if (thrown > 0) {
            this.saveCurrentShards(item, currentShards - thrown);
            if (level.isServerLevel()) {
                Point2D.Float dir = GameMath.normalize((float)x - player.x, (float)y - player.y);
                level.entityManager.pickups.add((new InventoryItem("enchantmentshard", thrown)).getPickupEntity(level, player.x, player.y, dir.x * 175.0F, dir.y * 175.0F));
            } else if (level.isClientLevel()) {
                SoundManager.playSound(GameResources.coins, SoundEffect.effect(player));
            }
        }

        return item;
    }

    public Supplier<ContainerActionResult> getInventoryRightClickAction(Container container, InventoryItem item, int slotIndex, ContainerSlot slot) {
        return container.client.playerMob.isInventoryExtended() ? () -> {
            int currentShards = this.getCurrentShards(item);
            if (currentShards > 0) {
                ContainerSlot clientDraggingSlot = container.getClientDraggingSlot();
                Item shardItem = ItemRegistry.getItem("enchantmentshard");
                int startItems = Math.min(currentShards, shardItem.getStackSize());
                InventoryItem shards = new InventoryItem(shardItem, startItems);
                if (clientDraggingSlot.isClear()) {
                    this.saveCurrentShards(item, currentShards - shards.getAmount());
                    clientDraggingSlot.setItem(shards);
                    return new ContainerActionResult(2657165);
                } else {
                    if (clientDraggingSlot.getItem().canCombine(container.client.playerMob.getLevel(), container.client.playerMob, shards, "pouchtransfer") && clientDraggingSlot.getItem().combine(container.client.playerMob.getLevel(), container.client.playerMob, clientDraggingSlot.getInventory(), clientDraggingSlot.getInventorySlot(), shards, shards.getAmount(), false, "pouchtransfer", (InventoryAddConsumer)null).success) {
                        int itemsCombined = startItems - shards.getAmount();
                        this.saveCurrentShards(item, currentShards - itemsCombined);
                    }

                    return new ContainerActionResult(10619587);
                }
            } else {
                return new ContainerActionResult(3401846);
            }
        } : null;
    }

    public boolean canCombineItem(Level level, PlayerMob player, InventoryItem me, InventoryItem him, String purpose) {
        if (him == null) {
            return false;
        } else {
            return this.getID() == him.item.getID() || this.combinePurposes.contains(purpose) && him.item.getStringID().equals("enchantmentshard");
        }
    }

    public boolean onCombine(Level level, PlayerMob player, Inventory myInventory, int mySlot, InventoryItem me, InventoryItem other, int maxStackSize, int amount, boolean combineIsNew, String purpose, InventoryAddConsumer addConsumer) {
        if (this.combinePurposes.contains(purpose) && other.item.getStringID().equals("enchantmentshard")) {
            this.saveCurrentShards(me, this.getCurrentShards(me) + amount);
            other.setAmount(other.getAmount() - amount);
            if (addConsumer != null) {
                addConsumer.add((Inventory)null, 0, amount);
            }

            return true;
        } else {
            return super.onCombine(level, player, myInventory, mySlot, me, other, maxStackSize, amount, combineIsNew, purpose, addConsumer);
        }
    }

    public ComparableSequence<Integer> getInventoryAddPriority(Level level, PlayerMob player, Inventory inventory, int inventorySlot, InventoryItem item, InventoryItem input, String purpose) {
        ComparableSequence<Integer> last = super.getInventoryAddPriority(level, player, inventory, inventorySlot, item, input, purpose);
        return input.item.getStringID().equals("enchantmentshard") && purpose.equals("itempickup") ? last.beforeBy(-10000) : last;
    }

    public int getInventoryAmount(Level level, PlayerMob player, InventoryItem item, Item requestItem, String purpose) {
        return purpose.equals("buy") && requestItem.getStringID().equals("enchantmentshard") ? this.getCurrentShards(item) : super.getInventoryAmount(level, player, item, requestItem, purpose);
    }

    public void countIngredientAmount(Level level, PlayerMob player, Inventory inventory, int inventorySlot, InventoryItem item, IngredientCounter handler) {
        handler.handle((Inventory)null, inventorySlot, new InventoryItem("enchantmentshard", this.getCurrentShards(item)));
        super.countIngredientAmount(level, player, inventory, inventorySlot, item, handler);
    }

    public boolean inventoryAddItem(Level level, PlayerMob player, Inventory myInventory, int mySlot, InventoryItem me, InventoryItem input, String purpose, boolean isValid, int stackLimit, boolean combineIsValid, InventoryAddConsumer addConsumer) {
        if (input.item.getStringID().equals("enchantmentshard") && (purpose.equals("itempickup") || purpose.equals("sell"))) {
            int amount = input.getAmount();
            this.saveCurrentShards(me, this.getCurrentShards(me) + amount);
            if (addConsumer != null) {
                addConsumer.add((Inventory)null, 0, amount);
            }

            input.setAmount(0);
            return true;
        } else {
            return super.inventoryAddItem(level, player, myInventory, mySlot, me, input, purpose, isValid, stackLimit, combineIsValid, addConsumer);
        }
    }

    public int inventoryCanAddItem(Level level, PlayerMob player, InventoryItem item, InventoryItem input, String purpose, boolean isValid, int stackLimit) {
        return input.item.getStringID().equals("enchantmentshard") ? input.getAmount() : super.inventoryCanAddItem(level, player, item, input, purpose, isValid, stackLimit);
    }

    public int removeInventoryAmount(Level level, PlayerMob player, InventoryItem item, Item requestItem, int amount, String purpose) {
        return requestItem.getStringID().equals("enchantmentshard") ? this.removeShards(item, amount) : super.removeInventoryAmount(level, player, item, requestItem, amount, purpose);
    }

    public int removeInventoryAmount(Level level, PlayerMob player, final InventoryItem item, Inventory inventory, int inventorySlot, Ingredient ingredient, int amount, Collection<InventoryItemsRemoved> collect) {
        Item shard = ItemRegistry.getItem("enchantmentshard");
        return ingredient.matchesItem(shard) ? this.removeShards(item, amount) : super.removeInventoryAmount(level, player, item, inventory, inventorySlot, ingredient, amount, collect);
    }

    private int removeShards(InventoryItem item, int amount) {
        int currentShards = this.getCurrentShards(item);
        int removedAmount = Math.min(currentShards, amount);
        currentShards -= removedAmount;
        this.saveCurrentShards(item, currentShards);
        return removedAmount;
    }

    protected int getCurrentShards(InventoryItem item) {
        return item.getGndData().getInt("enchantmentshard");
    }

    protected void saveCurrentShards(InventoryItem item, int shards) {
        item.getGndData().setInt("enchantmentshard", shards);
    }
}

