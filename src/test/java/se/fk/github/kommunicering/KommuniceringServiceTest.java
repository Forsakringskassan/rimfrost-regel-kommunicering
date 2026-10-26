package se.fk.github.kommunicering;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import se.fk.github.kommunicering.logic.KommuniceringService;
import se.fk.github.kommunicering.logic.exception.ErsattningDeserializationException;
import se.fk.rimfrost.adapter.folkbokford.FolkbokfordAdapter;
import se.fk.rimfrost.adapter.folkbokford.FolkbokfordException;
import se.fk.rimfrost.adapter.folkbokford.dto.FolkbokfordResponse;
import se.fk.rimfrost.adapter.folkbokford.dto.ImmutableFolkbokfordResponse;
import se.fk.rimfrost.ersattningdata.ErsattningData;
import se.fk.rimfrost.framework.handlaggning.model.Uppgiftsdatakoppling;
import se.fk.rimfrost.framework.regel.kommunicering.AbstractRegelKommuniceringTestBase;
import se.fk.rimfrost.framework.regel.kommunicering.logic.exception.CorrelationDataReadException;
import se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.exception.ExternalServiceErrorException;
import se.fk.rimfrost.regel.kommunicering.jaxrsspec.controllers.generatedsource.model.Beslutsutfall;
import se.fk.rimfrost.regel.kommunicering.jaxrsspec.controllers.generatedsource.model.Kund;
import se.fk.rimfrost.regel.kommunicering.jaxrsspec.controllers.generatedsource.model.PatchErsattningRequest;
import se.fk.rimfrost.regel.kommunicering.jaxrsspec.controllers.generatedsource.model.UpdateErsattning;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

@QuarkusTest
public class KommuniceringServiceTest extends AbstractRegelKommuniceringTestBase
{
   private static final String ERSATTNING = "ersattning";

   @Inject
   KommuniceringService kommuniceringService;

   @Inject
   ObjectMapper objectMapper;

   @InjectMock
   FolkbokfordAdapter folkbokfordAdapter;

   @Test
   @DisplayName("Läsning mappar kund från folkbokföringen och endast sakfrågor av typen ersättning")
   void should_map_kund_and_only_ersattning_containers_on_read() throws Exception
   {
      var ersattningData = KommuniceringTestData.ersattningData(1);
      var ersattningId = UUID.randomUUID();
      var handlaggning = KommuniceringTestData.handlaggning(List.of(
            KommuniceringTestData.container(ersattningId, ERSATTNING, objectMapper.writeValueAsString(ersattningData)),
            KommuniceringTestData.container(UUID.randomUUID(), "annan", "{}")));
      var folkbokfordResponse = ImmutableFolkbokfordResponse.builder()
            .id(UUID.randomUUID().toString())
            .fornamn(UUID.randomUUID().toString())
            .efternamn(UUID.randomUUID().toString())
            .kon(FolkbokfordResponse.Kon.KVINNA)
            .utdelningsadress(UUID.randomUUID().toString())
            .postnummer(UUID.randomUUID().toString())
            .postort(UUID.randomUUID().toString())
            .careOf(UUID.randomUUID().toString())
            .build();
      Mockito.when(folkbokfordAdapter.getFolkbokfordInfo(any())).thenReturn(folkbokfordResponse);

      var response = kommuniceringService.read(handlaggning);

      assertEquals(handlaggning.id(), response.getHandlaggningId());
      assertEquals(folkbokfordResponse.fornamn(), response.getKund().getFornamn());
      assertEquals(folkbokfordResponse.efternamn(), response.getKund().getEfternamn());
      assertEquals(Kund.KonEnum.KVINNA, response.getKund().getKon());
      assertEquals(1, response.getErsattningar().size());
      var ersattning = response.getErsattningar().getFirst();
      assertEquals(ersattningId, ersattning.getErsattningId());
      assertEquals(ersattningData.getErsattningstyp().id(), ersattning.getErsattningstyp());
      assertEquals(ersattningData.getOmfattningProcent(), ersattning.getOmfattningProcent());
      assertEquals(ersattningData.getBelopp(), ersattning.getBelopp());
      assertEquals(ersattningData.getBerakningsgrund(), ersattning.getBerakningsgrund());
      assertEquals(Beslutsutfall.JA, ersattning.getBeslutsutfall());
      assertNull(ersattning.getAvslagsanledning());
      assertEquals(ersattningData.getFrom(), ersattning.getFrom());
      assertEquals(ersattningData.getTom(), ersattning.getTom());
   }

   @Test
   @DisplayName("Läsning returnerar tom kund när personen inte finns i folkbokföringen")
   void should_return_empty_kund_on_read_when_folkbokford_not_found() throws Exception
   {
      var handlaggning = KommuniceringTestData.handlaggning(List.of());
      Mockito.when(folkbokfordAdapter.getFolkbokfordInfo(any()))
            .thenThrow(new FolkbokfordException(FolkbokfordException.ErrorType.NOT_FOUND, "not found"));

      var response = kommuniceringService.read(handlaggning);

      assertNull(response.getKund().getFornamn());
      assertNull(response.getKund().getEfternamn());
      assertNull(response.getKund().getKon());
   }

   @ParameterizedTest
   @EnumSource(value = FolkbokfordException.ErrorType.class, names = "NOT_FOUND", mode = EnumSource.Mode.EXCLUDE)
   @DisplayName("Läsning mappar fel från folkbokföringen till väldefinierade HTTP-statuskoder")
   void should_throw_external_service_error_on_read_when_folkbokford_fails(FolkbokfordException.ErrorType errorType)
         throws Exception
   {
      var handlaggning = KommuniceringTestData.handlaggning(List.of());
      Mockito.when(folkbokfordAdapter.getFolkbokfordInfo(any()))
            .thenThrow(new FolkbokfordException(errorType, "folkbokford error"));

      var exception = assertThrows(ExternalServiceErrorException.class, () -> kommuniceringService.read(handlaggning));

      var expectedStatus = switch (errorType)
      {
         case BAD_REQUEST -> Response.Status.BAD_REQUEST;
         case SERVICE_UNAVAILABLE -> Response.Status.SERVICE_UNAVAILABLE;
         default -> Response.Status.INTERNAL_SERVER_ERROR;
      };
      assertEquals(expectedStatus, exception.getStatus());
   }

   @Test
   @DisplayName("Läsning kastar ErsattningDeserializationException när ersättningens data inte kan tolkas")
   void should_throw_deserialization_exception_on_read_when_ersattning_data_is_invalid()
   {
      var handlaggning = KommuniceringTestData.handlaggning(List.of(
            KommuniceringTestData.container(UUID.randomUUID(), ERSATTNING, "{not json")));

      assertThrows(ErsattningDeserializationException.class, () -> kommuniceringService.read(handlaggning));
   }

   @Test
   @DisplayName("Uppdatering skriver nya värden och ny version till ersättningen och lämnar övriga sakfrågor orörda")
   void should_update_ersattning_container_and_keep_other_containers() throws Exception
   {
      var ersattningData = KommuniceringTestData.ersattningData(3);
      var updatedId = UUID.randomUUID();
      var untouchedErsattning = KommuniceringTestData.container(UUID.randomUUID(), ERSATTNING,
            objectMapper.writeValueAsString(KommuniceringTestData.ersattningData(1)));
      var annan = KommuniceringTestData.container(UUID.randomUUID(), "annan", "{}");
      var handlaggning = KommuniceringTestData.handlaggning(List.of(
            annan,
            KommuniceringTestData.container(updatedId, ERSATTNING, objectMapper.writeValueAsString(ersattningData)),
            untouchedErsattning));
      var updateErsattning = new UpdateErsattning(updatedId, 50, 250, 2000, LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 2, 28));
      updateErsattning.setBeslutsutfall(Beslutsutfall.NEJ);
      updateErsattning.setAvslagsanledning("anledning");

      var update = kommuniceringService.update(handlaggning,
            new PatchErsattningRequest(List.of(updateErsattning), "motivering"));

      var containers = update.handlaggning().yrkande().sakfragorStallningstaganden();
      assertEquals(3, containers.size());
      assertSame(annan, containers.get(0));
      assertSame(untouchedErsattning, containers.get(2));
      assertEquals(updatedId, containers.get(1).id());
      var stored = ErsattningData.fromJson(containers.get(1).data(), objectMapper);
      assertEquals(ersattningData.getId(), stored.getId());
      assertEquals(4, stored.getVersion());
      assertEquals(50, stored.getOmfattningProcent());
      assertEquals(250, stored.getBelopp());
      assertEquals(2000, stored.getBerakningsgrund());
      assertEquals(se.fk.rimfrost.ersattningdata.Beslutsutfall.NEJ, stored.getBeslutsutfall());
      assertEquals("anledning", stored.getAvslagsanledning());
      assertEquals(LocalDate.of(2026, 2, 1), stored.getFrom());
      assertEquals(LocalDate.of(2026, 2, 28), stored.getTom());
      assertEquals(ersattningData.getErsattningstyp(), stored.getErsattningstyp());
      assertEquals(ersattningData.getYrkandeStatus(), stored.getYrkandeStatus());
   }

   @Test
   @DisplayName("Uppdatering lägger motivering som kommentar och underlag som pekar på ersättningens tidigare version")
   void should_add_motivering_and_underlag_to_uppgift_on_update() throws Exception
   {
      var ersattningData = KommuniceringTestData.ersattningData(3);
      var ersattningId = UUID.randomUUID();
      var handlaggning = KommuniceringTestData.handlaggning(List.of(
            KommuniceringTestData.container(ersattningId, ERSATTNING, objectMapper.writeValueAsString(ersattningData))));
      var updateErsattning = new UpdateErsattning(ersattningId, 50, 250, 2000, LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 2, 28));

      var update = kommuniceringService.update(handlaggning,
            new PatchErsattningRequest(List.of(updateErsattning), "motivering"));

      var uppgift = update.uppgift();
      assertEquals(2, uppgift.version());
      assertEquals("motivering", uppgift.kommentar());
      assertEquals(1, uppgift.underlag().size());
      var underlag = (Uppgiftsdatakoppling) uppgift.underlag().getFirst();
      assertEquals(ersattningData.getId().toString(), underlag.informationsobjektId());
      assertEquals(3, underlag.informationsobjektversion());
   }

   @Test
   @DisplayName("Uppdatering kastar ErsattningDeserializationException när ersättningens data inte kan tolkas")
   void should_throw_deserialization_exception_on_update_when_ersattning_data_is_invalid()
   {
      var ersattningId = UUID.randomUUID();
      var handlaggning = KommuniceringTestData.handlaggning(List.of(
            KommuniceringTestData.container(ersattningId, ERSATTNING, "{not json")));
      var request = new PatchErsattningRequest(List.of(new UpdateErsattning(ersattningId, 50, 250, 2000,
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28))), "motivering");

      assertThrows(ErsattningDeserializationException.class, () -> kommuniceringService.update(handlaggning, request));
   }

   @Test
   @DisplayName("Uppdatering kastar CorrelationDataReadException när korrelationsdata saknas")
   void should_throw_correlation_data_read_exception_on_update_when_correlation_data_missing() throws Exception
   {
      var ersattningId = UUID.randomUUID();
      var handlaggning = KommuniceringTestData.handlaggning(List.of(
            KommuniceringTestData.container(ersattningId, ERSATTNING,
                  objectMapper.writeValueAsString(KommuniceringTestData.ersattningData(1)))));
      var request = new PatchErsattningRequest(List.of(new UpdateErsattning(ersattningId, 50, 250, 2000,
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28))), "motivering");
      Mockito.when(oulUppgiftService.getCorrelationData(handlaggning.id())).thenReturn(null);

      assertThrows(CorrelationDataReadException.class, () -> kommuniceringService.update(handlaggning, request));
   }
}
