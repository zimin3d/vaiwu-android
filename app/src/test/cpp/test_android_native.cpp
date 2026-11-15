#include "AndroidGame.hpp"

#include <cstdio>

using vaiwu::android::applyEquipment;
using namespace vaiwu::game;

namespace {

int failures = 0;

#define CHECK(cond)                                                              \
    do {                                                                         \
        if (!(cond)) {                                                           \
            std::printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond);          \
            ++failures;                                                          \
        }                                                                        \
    } while (0)

void testRejectedEquipmentReturnsFalse()
{
    RpgProgress progress;
    normalizeRpg(CharacterId::Rin, progress);

    CHECK(!applyEquipment(CharacterId::Rin, progress, -1, -1));
    CHECK(!applyEquipment(CharacterId::Rin, progress, kEquipSlotCount, -1));
    CHECK(!applyEquipment(CharacterId::Rin, progress, 0, -2));
    CHECK(!applyEquipment(CharacterId::Rin, progress, 0, kEquipItemCount));
    CHECK(!applyEquipment(CharacterId::Rin, progress, 0,
        static_cast<int>(EquipItem::SteelSword)));
    CHECK(progress.equipped[0] == -1);

    grantEquip(progress, EquipItem::SteelSword);
    CHECK(!applyEquipment(CharacterId::Rin, progress, 1,
        static_cast<int>(EquipItem::SteelSword)));
    CHECK(progress.equipped[0] == static_cast<int>(EquipItem::SteelSword));
    CHECK(progress.equipped[1] == -1);

    CHECK(!applyEquipment(CharacterId::Rin, progress, 0,
        static_cast<int>(EquipItem::MagicStaff)));
    CHECK(progress.equipped[0] == static_cast<int>(EquipItem::SteelSword));
}

void testAcceptedEquipmentReturnsTrue()
{
    RpgProgress progress;
    normalizeRpg(CharacterId::Rin, progress);
    grantEquip(progress, EquipItem::SteelSword);
    grantEquip(progress, EquipItem::MagicStaff);

    CHECK(applyEquipment(CharacterId::Rin, progress, 0,
        static_cast<int>(EquipItem::SteelSword)));
    CHECK(progress.equipped[0] == static_cast<int>(EquipItem::SteelSword));
    CHECK(applyEquipment(CharacterId::Rin, progress, 0,
        static_cast<int>(EquipItem::MagicStaff)));
    CHECK(progress.equipped[0] == static_cast<int>(EquipItem::MagicStaff));
    CHECK(applyEquipment(CharacterId::Rin, progress, 0, -1));
    CHECK(progress.equipped[0] == -1);
}

} // namespace

int main()
{
    testRejectedEquipmentReturnsFalse();
    testAcceptedEquipmentReturnsTrue();

    if (failures == 0) {
        std::printf("ALL ANDROID NATIVE TESTS PASSED\n");
        return 0;
    }
    std::printf("%d FAILURES\n", failures);
    return 1;
}
