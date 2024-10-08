package enchantmentmod.mob.network;

import enchantmentmod.Config;
import necesse.engine.network.NetworkClient;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.EnchantmentRegistry;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.sound.SoundEffect;
import necesse.engine.sound.SoundManager;
import necesse.engine.util.GameMath;
import necesse.engine.util.GameRandom;
import necesse.engine.util.TicketSystemList;
import necesse.entity.mobs.friendly.human.humanShop.MageHumanMob;
import necesse.gfx.GameResources;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerTempInventory;
import necesse.inventory.container.customAction.BooleanCustomAction;
import necesse.inventory.container.customAction.ContentCustomAction;
import necesse.inventory.container.customAction.EmptyCustomAction;
import necesse.inventory.container.mob.ShopContainer;
import necesse.inventory.container.slots.EnchantableSlot;
import necesse.inventory.enchants.Enchantable;
import necesse.inventory.enchants.ItemEnchantment;
import necesse.level.maps.hudManager.floatText.ItemPickupText;

import java.util.Objects;

public class EnchantmentMageContainer extends ShopContainer {
    public final EmptyCustomAction enchantButton;
    public final ContentCustomAction enchantButtonResponse;
    public final BooleanCustomAction setIsEnchanting;
    private boolean isEnchanting;
    public final int ENCHANT_SLOT;
    public MageHumanMob mageMob;
    public final long enchantCostSeed;
    public final PlayerTempInventory enchantInv;

    public EnchantmentMageContainer(final NetworkClient client, int uniqueSeed, MageHumanMob mob, PacketReader contentReader) {
        super(client, uniqueSeed, mob, contentReader.getNextContentPacket());
        Packet content = contentReader.getNextContentPacket();
        this.enchantInv = client.playerMob.getInv().applyTempInventoryPacket(content, (m) -> {
            return this.isClosed();
        });
        this.ENCHANT_SLOT = this.addSlot(new EnchantableSlot(this.enchantInv, 0));
        this.addInventoryQuickTransfer((s) -> {
            return this.isEnchanting;
        }, this.ENCHANT_SLOT, this.ENCHANT_SLOT);
        this.mageMob = mob;
        this.isEnchanting = false;
        this.enchantCostSeed = this.priceSeed * (long) GameRandom.prime(28);
        this.enchantButton = (EmptyCustomAction)this.registerAction(new EmptyCustomAction() {
            protected void run() {
            if (!client.isServerClient()) {
                return;
            }
            if (!EnchantmentMageContainer.this.canEnchant()) {
                EnchantmentMageContainer.this.getSlot(EnchantmentMageContainer.this.ENCHANT_SLOT).markDirty();
                return;
            }
            int enchantCost = EnchantmentMageContainer.this.getEnchantCost();
            short randomSeed = (short)GameRandom.globalRandom.nextInt();
            InventoryItem item = EnchantmentMageContainer.this.getSlot(EnchantmentMageContainer.this.ENCHANT_SLOT).getItem();
            ((Enchantable)item.item).setEnchantment(item, EnchantmentMageContainer.this.getBiasedEnchantmentID(item));
            if (client.getServerClient().achievementsLoaded()) {
                client.getServerClient().achievements().ENCHANT_ITEM.markCompleted(client.getServerClient());
            }
            client.playerMob.getInv().main.removeItems(
                client.playerMob.getLevel(),
                client.playerMob,
                ItemRegistry.getItem("enchantmentorb"),
                enchantCost,
                "enchantment"
            );
            client.getServerClient().newStats.items_enchanted.increment(1);
            Packet itemContent = InventoryItem.getContentPacket(item);
            EnchantmentMageContainer.this.enchantButtonResponse.runAndSend(itemContent);
            EnchantmentMageContainer.this.getSlot(EnchantmentMageContainer.this.ENCHANT_SLOT).markDirty();
            }
        });
        this.enchantButtonResponse = (ContentCustomAction)this.registerAction(new ContentCustomAction() {
            protected void run(Packet content) {
            if (client.isClientClient()) {
                InventoryItem enchantedItem = InventoryItem.fromContentPacket(content);
                client.playerMob.getLevel().hudManager.addElement(new ItemPickupText(client.playerMob, enchantedItem));
                SoundManager.playSound(GameResources.pop, SoundEffect.effect(client.playerMob));
            }
            }
        });
        this.setIsEnchanting = (BooleanCustomAction)this.registerAction(new BooleanCustomAction() {
            protected void run(boolean value) {
                EnchantmentMageContainer.this.isEnchanting = value;
            }
        });
    }

    public int getEnchantCost() {
        if (this.getSlot(this.ENCHANT_SLOT).isClear()) {
            return 0;
        } else {
            int costs = Config.getInstance().getEnchantmentCosts();
            if (this.settlerHappiness != 0) {
                costs = costs - (int)(this.settlerHappiness / 10);
            }
            if (this.settlerHappiness > 0) {
                costs = costs - 1;
            }
            if (costs < 2) {
                costs = 2;
            }
            InventoryItem item = this.getSlot(this.ENCHANT_SLOT).getItem();
            return item.item.isEnchantable(item) ? costs : 0;
        }
    }

    public boolean isItemEnchantable() {
        if (this.getSlot(this.ENCHANT_SLOT).isClear()) {
            return false;
        } else {
            InventoryItem item = this.getSlot(this.ENCHANT_SLOT).getItem();
            return item.item.isEnchantable(item);
        }
    }

    public boolean canEnchant() {
        return this.isItemEnchantable() && client.playerMob.getInv().getAmount(
            ItemRegistry.getItem("enchantmentorb"),
            true,
            false,
            false,
            false,
            "buy"
        ) >= this.getEnchantCost();
    }

    public static Packet getMageContainerContent(MageHumanMob mob, ServerClient client) {
        Packet packet = new Packet();
        PacketWriter writer = new PacketWriter(packet);
        writer.putNextContentPacket(mob.getShopItemsContentPacket(client));
        writer.putNextContentPacket(client.playerMob.getInv().getTempInventoryPacket(1));
        return packet;
    }

    private int getBiasedEnchantmentID(InventoryItem item) {
        if (item.item.isEnchantable(item)) {
            Enchantable<?> enchantItem = (Enchantable)item.item;
            ItemEnchantment[] positiveEnchantments = (ItemEnchantment[])enchantItem.getValidEnchantmentIDs(item).stream().map(EnchantmentRegistry::getEnchantment).filter(Objects::nonNull).filter((e) -> {
                return e.getEnchantCostMod() >= 1.0F;
            }).toArray((x$0) -> {
                return new ItemEnchantment[x$0];
            });
            ItemEnchantment[] negativeEnchantments = (ItemEnchantment[])enchantItem.getValidEnchantmentIDs(item).stream().map(EnchantmentRegistry::getEnchantment).filter(Objects::nonNull).filter((e) -> {
                return e.getEnchantCostMod() <= 1.0F;
            }).toArray((x$0) -> {
                return new ItemEnchantment[x$0];
            });
            float happinessMultiplier = 0.05F;
            float lotteryBias = 0.0F;
            int settlerHappiness = GameMath.limit(this.settlerHappiness, 0, 100);
            TicketSystemList<ItemEnchantment> lottery = new TicketSystemList();
            if (settlerHappiness > 50) {
                lotteryBias = (float)(settlerHappiness - 50) * happinessMultiplier;
            } else if (settlerHappiness < 50) {
                lotteryBias = (float)(-settlerHappiness + 50) * happinessMultiplier;
            }

            ItemEnchantment[] var10 = positiveEnchantments;
            int var11 = positiveEnchantments.length;

            float enchantCostMod;
            int var12;
            ItemEnchantment negativeEnchantment;
            for(var12 = 0; var12 < var11; ++var12) {
                negativeEnchantment = var10[var12];
                enchantCostMod = (negativeEnchantment.getEnchantCostMod() - 1.0F) * 100.0F;
                if (settlerHappiness > 50) {
                    lottery.addObject(100 + (int)(lotteryBias * enchantCostMod), negativeEnchantment);
                } else {
                    lottery.addObject(100 - (int)(lotteryBias * enchantCostMod), negativeEnchantment);
                }
            }

            var10 = negativeEnchantments;
            var11 = negativeEnchantments.length;

            for(var12 = 0; var12 < var11; ++var12) {
                negativeEnchantment = var10[var12];
                enchantCostMod = (negativeEnchantment.getEnchantCostMod() - 1.0F) * 100.0F;
                if (settlerHappiness > 50) {
                    lottery.addObject(100 + (int)(lotteryBias * enchantCostMod), negativeEnchantment);
                } else {
                    lottery.addObject(100 - (int)(lotteryBias * enchantCostMod), negativeEnchantment);
                }
            }

            ItemEnchantment randomObject = (ItemEnchantment)lottery.getRandomObject(GameRandom.globalRandom);
            return EnchantmentRegistry.getEnchantmentID(randomObject.getStringID());
        } else {
            return 0;
        }
    }
}
