package se.fk.github.kommunicering.logic;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.github.kommunicering.logic.exception.ErsattningDeserializationException;
import se.fk.rimfrost.adapter.folkbokford.dto.FolkbokfordResponse;
import se.fk.rimfrost.ersattningdata.ErsattningData;
import se.fk.rimfrost.framework.handlaggning.model.Handlaggning;
import se.fk.rimfrost.framework.handlaggning.model.SakfragaStallningstagandeContainer;
import se.fk.rimfrost.regel.kommunicering.jaxrsspec.controllers.generatedsource.model.Beslutsutfall;
import se.fk.rimfrost.regel.kommunicering.jaxrsspec.controllers.generatedsource.model.Ersattning;
import se.fk.rimfrost.regel.kommunicering.jaxrsspec.controllers.generatedsource.model.GetDataResponse;
import se.fk.rimfrost.regel.kommunicering.jaxrsspec.controllers.generatedsource.model.Kund;

import java.util.ArrayList;

@ApplicationScoped
public class KommuniceringMapper
{
   private static final Logger LOGGER = LoggerFactory.getLogger(KommuniceringMapper.class);

   @Inject
   ObjectMapper objectMapper;

   public GetDataResponse toGetDataResponse(Handlaggning handlaggning, FolkbokfordResponse folkbokfordResponse,
         String ersattningObjectTypId)
   {
      var kund = new Kund();

      if (folkbokfordResponse != null)
      {
         kund.setFornamn(folkbokfordResponse.fornamn());
         kund.setEfternamn(folkbokfordResponse.efternamn());
         kund.setKon(mapKonEnum(folkbokfordResponse.kon()));
      }

      var ersattningResult = handlaggning.yrkande().sakfragorStallningstaganden().stream()
            .filter(pr -> pr.objektTypId().equalsIgnoreCase(ersattningObjectTypId)).toList();

      var ersattningsList = new ArrayList<Ersattning>();
      for (var yrkandeErsattning : ersattningResult)
      {
         ersattningsList.add(toErsattning(yrkandeErsattning, objectMapper));
      }

      var response = new GetDataResponse();
      response.setKund(kund);
      response.handlaggningId(handlaggning.id());
      response.setErsattningar(ersattningsList);

      return response;
   }

   private Kund.KonEnum mapKonEnum(FolkbokfordResponse.Kon kon)
    {
        return switch (kon) {
            case MAN -> Kund.KonEnum.MAN;
            case KVINNA -> Kund.KonEnum.KVINNA;
        };
    }

   public Ersattning toErsattning(SakfragaStallningstagandeContainer sakfragaStallningstagande, ObjectMapper objectMapper)
   {
      ErsattningData ersattningData;
      try
      {
         ersattningData = ErsattningData.fromJson(sakfragaStallningstagande.data(), objectMapper);
      }
      catch (RuntimeException e)
      {
         LOGGER.error("Failed to parse json as ErsattningData", e);
         throw new ErsattningDeserializationException();
      }

      var ersattning = new Ersattning();
      ersattning.setErsattningId(sakfragaStallningstagande.id());
      ersattning.setErsattningstyp(ersattningData.getErsattningstyp().id());
      ersattning.setOmfattningProcent(ersattningData.getOmfattningProcent());
      ersattning.setBelopp(ersattningData.getBelopp());
      ersattning.setBerakningsgrund(ersattningData.getBerakningsgrund());
      ersattning.setAvslagsanledning(ersattningData.getAvslagsanledning());
      ersattning.setBeslutsutfall(toBeslutsutfall(ersattningData.getBeslutsutfall()));
      ersattning.setFrom(ersattningData.getFrom());
      ersattning.setTom(ersattningData.getTom());
      return ersattning;
   }

   private Beslutsutfall toBeslutsutfall(se.fk.rimfrost.ersattningdata.Beslutsutfall beslutsutfall)
    {
        return switch (beslutsutfall) {
            case null -> null;
            case JA -> Beslutsutfall.JA;
            case NEJ -> Beslutsutfall.NEJ;
            case FU -> Beslutsutfall.FU;
        };
    }
}
