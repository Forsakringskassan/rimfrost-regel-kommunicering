package se.fk.github.kommunicering;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import se.fk.rimfrost.ersattningdata.ErsattningData;
import se.fk.rimfrost.framework.handlaggning.adapter.HandlaggningAdapter;
import se.fk.rimfrost.framework.handlaggning.exception.HandlaggningException;
import se.fk.rimfrost.framework.handlaggning.model.Handlaggning;
import se.fk.rimfrost.framework.handlaggning.model.HandlaggningUpdate;
import se.fk.rimfrost.framework.regel.kommunicering.AbstractRegelKommuniceringTestBase;
import se.fk.rimfrost.framework.regel.oul.jaxrsspec.controllers.generatedsource.model.ErrorResponse;
import se.fk.rimfrost.framework.sid.adapter.SidAdapter;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@QuarkusTest
public class KommuniceringControllerTest extends AbstractRegelKommuniceringTestBase
{
   private static final String PATH = "/regel/kommunicering/";

   @Inject
   ObjectMapper objectMapper;

   @InjectMock
   HandlaggningAdapter handlaggningAdapter;

   @InjectMock
   SidAdapter sidAdapter;

   private UUID ersattningId;

   private UUID annanId;

   private Handlaggning handlaggning;

   @BeforeEach
   void setUp() throws Exception
   {
      ersattningId = UUID.randomUUID();
      annanId = UUID.randomUUID();
      handlaggning = KommuniceringTestData.handlaggning(List.of(
            KommuniceringTestData.container(ersattningId, "ersattning",
                  objectMapper.writeValueAsString(KommuniceringTestData.ersattningData(1))),
            KommuniceringTestData.container(annanId, "annan", "{}")));
      Mockito.when(handlaggningAdapter.readHandlaggning(handlaggning.id())).thenReturn(handlaggning);
      Mockito.when(sidAdapter.containsSid(any())).thenReturn(false);
   }

   private static String updateErsattningJson(UUID id)
   {
      return """
            {"ersattningId": "%s", "omfattning_procent": 50, "belopp": 250, "berakningsgrund": 2000,
             "from": "2026-02-01", "tom": "2026-02-28"}""".formatted(id);
   }

   private static String patchRequestJson(String... ersattningar)
   {
      return """
            {"ersattningar": [%s], "motivering": "motivering"}""".formatted(String.join(",", ersattningar));
   }

   private ErrorResponse patchExpectingError(String body, int expectedStatus)
   {
      return RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .patch(PATH + handlaggning.id())
            .then()
            .statusCode(expectedStatus).extract().as(ErrorResponse.class);
   }

   @Test
   @DisplayName("PATCH utan beslutsutfall och avslagsanledning returnerar 204 och sparar null")
   void should_return_204_and_store_null_on_patch_without_beslutsutfall_and_avslagsanledning() throws Exception
   {
      RestAssured.given()
            .contentType(ContentType.JSON)
            .body(patchRequestJson(updateErsattningJson(ersattningId)))
            .patch(PATH + handlaggning.id())
            .then()
            .statusCode(204);

      var captor = ArgumentCaptor.forClass(HandlaggningUpdate.class);
      verify(handlaggningAdapter).updateHandlaggning(captor.capture());
      var stored = ErsattningData.fromJson(
            captor.getValue().handlaggning().yrkande().sakfragorStallningstaganden().getFirst().data(), objectMapper);
      assertNull(stored.getBeslutsutfall());
      assertNull(stored.getAvslagsanledning());
   }

   @Test
   @DisplayName("PATCH utan obligatoriskt fält returnerar 400 och uppdaterar inte handläggningen")
   void should_return_400_on_patch_when_required_field_missing() throws HandlaggningException
   {
      var body = patchRequestJson("""
            {"ersattningId": "%s", "omfattning_procent": 50, "berakningsgrund": 2000,
             "from": "2026-02-01", "tom": "2026-02-28"}""".formatted(ersattningId));

      RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .patch(PATH + handlaggning.id())
            .then()
            .statusCode(400);

      verify(handlaggningAdapter, never()).updateHandlaggning(any());
   }

   @Test
   @DisplayName("PATCH med enbart null i ersattningar returnerar 400")
   void should_return_400_on_patch_when_ersattningar_only_contains_null() throws HandlaggningException
   {
      var response = patchExpectingError(patchRequestJson("null"), 400);

      assertEquals(400, response.getCode());
      assertEquals("No updated ersattning provided", response.getMessage());
      verify(handlaggningAdapter, never()).updateHandlaggning(any());
   }

   @Test
   @DisplayName("PATCH med samma ersattningId flera gånger returnerar 400")
   void should_return_400_on_patch_when_ersattning_id_is_duplicated() throws HandlaggningException
   {
      var response = patchExpectingError(
            patchRequestJson(updateErsattningJson(ersattningId), updateErsattningJson(ersattningId)), 400);

      assertEquals(400, response.getCode());
      assertEquals("Failed to process request due to duplicate ersattning ID", response.getMessage());
      verify(handlaggningAdapter, never()).updateHandlaggning(any());
   }

   @Test
   @DisplayName("PATCH med id för en sakfråga som inte är en ersättning returnerar 400")
   void should_return_400_on_patch_when_ersattning_id_refers_to_other_objekttyp() throws HandlaggningException
   {
      var response = patchExpectingError(patchRequestJson(updateErsattningJson(annanId)), 400);

      assertEquals(400, response.getCode());
      assertEquals("Failed to locate ersattning with id " + annanId, response.getMessage());
      verify(handlaggningAdapter, never()).updateHandlaggning(any());
   }
}
