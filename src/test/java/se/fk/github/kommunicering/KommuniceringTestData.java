package se.fk.github.kommunicering;

import se.fk.rimfrost.ersattningdata.Beslutsutfall;
import se.fk.rimfrost.ersattningdata.ErsattningData;
import se.fk.rimfrost.ersattningdata.ErsattningsTyp;
import se.fk.rimfrost.framework.handlaggning.model.Handlaggning;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableHandlaggning;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableIdtyp;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableRollIYrkande;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableSakfragaStallningstagandeContainer;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableYrkande;
import se.fk.rimfrost.framework.handlaggning.model.SakfragaStallningstagandeContainer;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Builds real immutables, since {@code ImmutableHandlaggning.builder().from(...)} in
 * {@code KommuniceringService.update} rejects Mockito mocks with null accessors.
 */
final class KommuniceringTestData
{
   private KommuniceringTestData()
   {
   }

   static Handlaggning handlaggning(List<SakfragaStallningstagandeContainer> containers)
   {
      var roll = ImmutableRollIYrkande.builder()
            .id(UUID.randomUUID())
            .individ(ImmutableIdtyp.builder().typId("personnummer").varde("19900101-1234").build())
            .yrkandeRollId("sokande")
            .avserYrkande(UUID.randomUUID())
            .build();
      var yrkande = ImmutableYrkande.builder()
            .id(UUID.randomUUID())
            .version(1)
            .ingangtypId("ingangtyp")
            .yrkandeDatum(OffsetDateTime.now())
            .yrkandeStatus("PAGAENDE")
            .yrkandeFrom(OffsetDateTime.now())
            .yrkandeTom(OffsetDateTime.now().plusDays(10))
            .avsikt("avsikt")
            .rollerIYrkande(List.of(roll))
            .sakfragorStallningstaganden(containers)
            .beslut(List.of())
            .build();
      return ImmutableHandlaggning.builder()
            .id(UUID.randomUUID())
            .version(1)
            .yrkande(yrkande)
            .handlaggningIdTyp("handlaggningIdTyp")
            .skapadTS(OffsetDateTime.now())
            .handlaggningspecifikationId(UUID.randomUUID())
            .build();
   }

   static SakfragaStallningstagandeContainer container(UUID id, String objektTypId, String data)
   {
      return ImmutableSakfragaStallningstagandeContainer.builder()
            .id(id)
            .objektTypId(objektTypId)
            .data(data)
            .build();
   }

   static ErsattningData ersattningData(int version)
   {
      return new ErsattningData(UUID.randomUUID(), version, new ErsattningsTyp("typ-1", "Typ 1"), 100, 500, 1000,
            Beslutsutfall.JA, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null, "PAGAENDE");
   }
}
