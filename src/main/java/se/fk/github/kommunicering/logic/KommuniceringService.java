package se.fk.github.kommunicering.logic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.github.kommunicering.logic.exception.EmptyErsattningListException;
import se.fk.github.kommunicering.logic.exception.ErsattningDeserializationException;
import se.fk.github.kommunicering.logic.exception.ErsattningDuplicateIdException;
import se.fk.github.kommunicering.logic.exception.ErsattningNotFoundException;
import se.fk.github.kommunicering.logic.exception.ErsattningSerializationException;
import se.fk.rimfrost.adapter.folkbokford.FolkbokfordAdapter;
import se.fk.rimfrost.adapter.folkbokford.FolkbokfordException;
import se.fk.rimfrost.adapter.folkbokford.dto.FolkbokfordResponse;
import se.fk.rimfrost.adapter.folkbokford.dto.ImmutableFolkbokfordRequest;
import se.fk.rimfrost.ersattningdata.Beslutsutfall;
import se.fk.rimfrost.ersattningdata.ErsattningData;
import se.fk.rimfrost.framework.handlaggning.model.Handlaggning;
import se.fk.rimfrost.framework.handlaggning.model.HandlaggningUpdate;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableHandlaggning;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableHandlaggningUpdate;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableSakfragaStallningstagandeContainer;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableUppgift;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableUppgiftsdatakoppling;
import se.fk.rimfrost.framework.handlaggning.model.ImmutableYrkande;
import se.fk.rimfrost.framework.handlaggning.model.SakfragaStallningstagandeContainer;
import se.fk.rimfrost.framework.handlaggning.model.Uppgiftsdata;
import se.fk.rimfrost.framework.handlaggning.model.Yrkande;
import se.fk.rimfrost.framework.regel.kommunicering.logic.RegelKommuniceringService;
import se.fk.rimfrost.framework.regel.kommunicering.logic.exception.CorrelationDataReadException;
import se.fk.rimfrost.framework.regel.kommunicering.presentation.rest.exception.ExternalServiceErrorException;
import se.fk.rimfrost.framework.regel.oul.logic.OulUppgiftService;
import se.fk.rimfrost.regel.kommunicering.jaxrsspec.controllers.generatedsource.model.GetDataResponse;
import se.fk.rimfrost.regel.kommunicering.jaxrsspec.controllers.generatedsource.model.PatchErsattningRequest;
import se.fk.rimfrost.regel.kommunicering.jaxrsspec.controllers.generatedsource.model.UpdateErsattning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class KommuniceringService implements RegelKommuniceringService<GetDataResponse, PatchErsattningRequest>
{
   private static final Logger LOGGER = LoggerFactory.getLogger(KommuniceringService.class);
   private static final String ERSATTNING_OBJECT_TYP_ID = "ersattning";

   @Inject
   FolkbokfordAdapter folkbokfordAdapter;

   @Inject
   KommuniceringMapper kommuniceringMapper;

   @Inject
   ObjectMapper objectMapper;

   @Inject
   OulUppgiftService oulUppgiftService;

   @Override
   public GetDataResponse read(Handlaggning handlaggning)
   {
      var indvidyrkandeRoll = handlaggning.yrkande().rollerIYrkande().getFirst();
      var folkbokfordResponse = getFolkbokfordInfo(indvidyrkandeRoll.individ().varde(), handlaggning.id());

      return kommuniceringMapper.toGetDataResponse(handlaggning, folkbokfordResponse, ERSATTNING_OBJECT_TYP_ID);
   }

   @Override
   public HandlaggningUpdate update(Handlaggning handlaggning, PatchErsattningRequest request)
   {
      var ersattningUpdates = request.getErsattningar().stream().filter(Objects::nonNull).toList();
      var ersattningIdCount = ersattningUpdates.stream().map(UpdateErsattning::getErsattningId).distinct().count();

      if (ersattningIdCount < 1)
      {
         throw new EmptyErsattningListException();
      }

      if (ersattningIdCount != ersattningUpdates.size())
      {
         throw new ErsattningDuplicateIdException();
      }

      var correlation = oulUppgiftService.getCorrelationData(handlaggning.id());

      if (correlation == null)
      {
         throw new CorrelationDataReadException("Failed to read correlation data");
      }

      var ersattningContainersById = handlaggning.yrkande().sakfragorStallningstaganden().stream()
            .filter(c -> c.objektTypId().equalsIgnoreCase(ERSATTNING_OBJECT_TYP_ID))
            .collect(Collectors.toMap(SakfragaStallningstagandeContainer::id, Function.identity(), (first, second) -> first));

      Map<UUID, SakfragaStallningstagandeContainer> updatedErsattningContainersById = new HashMap<>();
      List<Uppgiftsdata> underlag = new ArrayList<>();
      for (var ersattningUpdate : ersattningUpdates)
      {
         var ersattningId = ersattningUpdate.getErsattningId();
         var container = ersattningContainersById.get(ersattningId);

         if (container == null)
         {
            LOGGER.warn("Failed to locate SakfragaStallningstagandeContainer with id {} for handlaggning with id {}",
                  ersattningId, handlaggning.id());
            throw new ErsattningNotFoundException("Failed to locate ersattning with id " + ersattningId);
         }

         var ersattning = parseErsattning(container);

         underlag.add(ImmutableUppgiftsdatakoppling.builder().informationsobjektId(ersattning.getId().toString())
               .informationsobjektversion(ersattning.getVersion()).build());

         updatedErsattningContainersById.put(container.id(), createUpdatedContainer(container, ersattning, ersattningUpdate));
      }

      var updatedYrkande = createUpdatedYrkande(handlaggning.yrkande(), updatedErsattningContainersById);

      var updatedHandlaggning = ImmutableHandlaggning.builder()
            .from(handlaggning)
            .yrkande(updatedYrkande)
            .build();

      var updatedUppgift = ImmutableUppgift.builder()
            .from(correlation.uppgift())
            .version(correlation.uppgift().version() + 1)
            .addAllUnderlag(underlag)
            .kommentar(request.getMotivering())
            .build();

      return ImmutableHandlaggningUpdate.builder()
            .handlaggning(updatedHandlaggning)
            .uppgift(updatedUppgift)
            .build();
   }

   private FolkbokfordResponse getFolkbokfordInfo(String personnummer, UUID handlaggningId)
    {
        var folkbokfordRequest = ImmutableFolkbokfordRequest.builder()
                .personnummer(personnummer)
                .build();
        try
        {
            return folkbokfordAdapter.getFolkbokfordInfo(folkbokfordRequest);
        }
        catch (FolkbokfordException e)
        {
            if (e.getErrorType() == FolkbokfordException.ErrorType.NOT_FOUND)
            {
                return null;
            }

            LOGGER.error("Folkbokford adapter failed to read folkbokford data with {}: {} for handlaggning with id {}", e.getErrorType(), e.getMessage(), handlaggningId);
            throw switch (e.getErrorType())
            {
                case BAD_REQUEST -> new ExternalServiceErrorException(Response.Status.BAD_REQUEST, e.getMessage(), e);
                case SERVICE_UNAVAILABLE -> new ExternalServiceErrorException(Response.Status.SERVICE_UNAVAILABLE, e.getMessage(), e);
                default -> new ExternalServiceErrorException(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage(), e);
            };
        }
    }

   private ErsattningData parseErsattning(SakfragaStallningstagandeContainer container)
   {
      try
      {
         return ErsattningData.fromJson(container.data(), objectMapper);
      }
      catch (RuntimeException e)
      {
         LOGGER.error("Failed to parse json as ErsattningData", e);
         throw new ErsattningDeserializationException();
      }
   }

   private SakfragaStallningstagandeContainer createUpdatedContainer(SakfragaStallningstagandeContainer container,
         ErsattningData ersattning, UpdateErsattning updateErsattning)
   {
      ersattning.setVersion(ersattning.getVersion() + 1);
      ersattning.setBelopp(updateErsattning.getBelopp());
      ersattning.setBerakningsgrund(updateErsattning.getBerakningsgrund());
      ersattning.setOmfattningProcent(updateErsattning.getOmfattningProcent());
      ersattning.setFrom(updateErsattning.getFrom());
      ersattning.setTom(updateErsattning.getTom());
      ersattning.setAvslagsanledning(updateErsattning.getAvslagsanledning());
      ersattning.setBeslutsutfall(toBeslutsutfall(updateErsattning.getBeslutsutfall()));

      String updatedData;
      try
      {
         updatedData = objectMapper.writeValueAsString(ersattning);
      }
      catch (JsonProcessingException e)
      {
         LOGGER.error("Failed to serialize updated ersattning", e);
         throw new ErsattningSerializationException();
      }

      return ImmutableSakfragaStallningstagandeContainer.builder()
            .from(container)
            .data(updatedData)
            .build();
   }

   private Beslutsutfall toBeslutsutfall(se.fk.rimfrost.regel.kommunicering.jaxrsspec.controllers.generatedsource.model.Beslutsutfall beslutsutfall)
    {
        return switch (beslutsutfall) {
            case null -> null;
            case JA -> Beslutsutfall.JA;
            case NEJ ->  Beslutsutfall.NEJ;
            case FU ->  Beslutsutfall.FU;
        };
    }

   private Yrkande createUpdatedYrkande(Yrkande yrkande,
         Map<UUID, SakfragaStallningstagandeContainer> updatedContainersById)
   {
      var updatedSakfragaStallningstagandeContainerList = yrkande.sakfragorStallningstaganden().stream()
            .map(c -> updatedContainersById.getOrDefault(c.id(), c))
            .toList();

      return ImmutableYrkande.builder()
            .from(yrkande)
            .sakfragorStallningstaganden(updatedSakfragaStallningstagandeContainerList)
            .build();
   }
}
