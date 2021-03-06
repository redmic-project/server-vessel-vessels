package es.redmic.vesselslib.events.vessel;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import es.redmic.brokerlib.avro.common.Event;
import es.redmic.brokerlib.avro.common.EventError;
import es.redmic.exception.common.ExceptionType;
import es.redmic.exception.common.InternalException;
import es.redmic.vesselslib.dto.vessel.VesselDTO;
import es.redmic.vesselslib.events.vessel.common.VesselCancelledEvent;
import es.redmic.vesselslib.events.vessel.common.VesselEvent;
import es.redmic.vesselslib.events.vessel.create.CreateVesselCancelledEvent;
import es.redmic.vesselslib.events.vessel.create.CreateVesselConfirmedEvent;
import es.redmic.vesselslib.events.vessel.create.CreateVesselEnrichedEvent;
import es.redmic.vesselslib.events.vessel.create.CreateVesselEvent;
import es.redmic.vesselslib.events.vessel.create.CreateVesselFailedEvent;
import es.redmic.vesselslib.events.vessel.create.VesselCreatedEvent;
import es.redmic.vesselslib.events.vessel.delete.DeleteVesselCancelledEvent;
import es.redmic.vesselslib.events.vessel.delete.DeleteVesselCheckFailedEvent;
import es.redmic.vesselslib.events.vessel.delete.DeleteVesselCheckedEvent;
import es.redmic.vesselslib.events.vessel.delete.DeleteVesselConfirmedEvent;
import es.redmic.vesselslib.events.vessel.delete.DeleteVesselEvent;
import es.redmic.vesselslib.events.vessel.delete.DeleteVesselFailedEvent;
import es.redmic.vesselslib.events.vessel.delete.VesselDeletedEvent;
import es.redmic.vesselslib.events.vessel.partialupdate.vesseltype.UpdateVesselTypeInVesselEvent;
import es.redmic.vesselslib.events.vessel.update.UpdateVesselCancelledEvent;
import es.redmic.vesselslib.events.vessel.update.UpdateVesselConfirmedEvent;
import es.redmic.vesselslib.events.vessel.update.UpdateVesselEnrichedEvent;
import es.redmic.vesselslib.events.vessel.update.UpdateVesselEvent;
import es.redmic.vesselslib.events.vessel.update.UpdateVesselFailedEvent;
import es.redmic.vesselslib.events.vessel.update.VesselUpdatedEvent;
import es.redmic.vesselslib.events.vesseltype.VesselTypeEventTypes;
import es.redmic.vesselslib.events.vesseltype.common.VesselTypeEvent;

public class VesselEventFactory {

	private static Logger logger = LogManager.getLogger();

	public static Event getEvent(Event source, String type) {

		if (type.equals(VesselEventTypes.DELETE)) {

			logger.debug("Creando evento DeleteVesselEvent para: " + source.getAggregateId());

			return new DeleteVesselEvent().buildFrom(source);
		}

		if (type.equals(VesselEventTypes.DELETE_CHECKED)) {

			logger.debug("Creando evento DeleteVesselCheckedEvent para: " + source.getAggregateId());

			return new DeleteVesselCheckedEvent().buildFrom(source);
		}

		if (type.equals(VesselEventTypes.CREATE_CONFIRMED)) {

			logger.debug("Creando evento CreateVesselConfirmedEvent para: " + source.getAggregateId());

			return new CreateVesselConfirmedEvent().buildFrom(source);
		}

		if (type.equals(VesselEventTypes.UPDATE_CONFIRMED)) {

			logger.debug("Creando evento UpdateVesselConfirmedEvent para: " + source.getAggregateId());

			return new UpdateVesselConfirmedEvent().buildFrom(source);
		}

		if (type.equals(VesselEventTypes.DELETE_CONFIRMED)) {

			logger.debug("Creando evento DeleteVesselConfirmedEvent para: " + source.getAggregateId());

			return new DeleteVesselConfirmedEvent().buildFrom(source);
		}

		if (type.equals(VesselEventTypes.DELETED)) {

			logger.debug("Creando evento VesselDeletedEvent para: " + source.getAggregateId());

			return new VesselDeletedEvent().buildFrom(source);
		}

		logger.error("Tipo de evento no soportado");
		throw new InternalException(ExceptionType.INTERNAL_EXCEPTION);
	}

	//////////////

	public static Event getEvent(Event source, String type, VesselDTO vessel) {

		VesselEvent successfulEvent = null;

		if (type.equals(VesselEventTypes.CREATE_ENRICHED)) {

			logger.debug("Creando evento CreateVesselEnrichedEvent para: " + source.getAggregateId());

			successfulEvent = new CreateVesselEnrichedEvent().buildFrom(source);
		}

		if (type.equals(VesselEventTypes.UPDATE_ENRICHED)) {

			logger.debug("Creando evento UpdateVesselEnrichedEvent para: " + source.getAggregateId());

			successfulEvent = new UpdateVesselEnrichedEvent().buildFrom(source);
		}

		if (type.equals(VesselEventTypes.CREATE)) {

			logger.debug("Creando evento CreateVesselEvent para: " + source.getAggregateId());
			successfulEvent = new CreateVesselEvent().buildFrom(source);
		}

		if (type.equals(VesselEventTypes.UPDATE)) {

			logger.debug("Creando evento UpdateVesselEvent para: " + source.getAggregateId());
			successfulEvent = new UpdateVesselEvent().buildFrom(source);
		}

		if (type.equals(VesselEventTypes.CREATED)) {

			logger.debug("Creando evento VesselCreatedEvent para: " + source.getAggregateId());
			successfulEvent = new VesselCreatedEvent().buildFrom(source);
		}

		if (type.equals(VesselEventTypes.UPDATED)) {

			logger.debug("Creando evento VesselUpdatedEvent para: " + source.getAggregateId());
			successfulEvent = new VesselUpdatedEvent().buildFrom(source);
		}

		if (successfulEvent != null) {
			successfulEvent.setVessel(vessel);
			return successfulEvent;
		} else {
			logger.error("Tipo de evento no soportado");
			throw new InternalException(ExceptionType.INTERNAL_EXCEPTION);
		}
	}

	//////////////

	public static Event getEvent(Event source, Event trigger, String type) {

		if (type.equals(VesselEventTypes.UPDATE_VESSELTYPE)) {

			logger.debug("Creando evento UpdateVesselTypeInVesselEvent para: " + source.getAggregateId());

			UpdateVesselTypeInVesselEvent requestEvent = new UpdateVesselTypeInVesselEvent();
			requestEvent.setAggregateId(source.getAggregateId());
			requestEvent.setUserId(trigger.getUserId());
			requestEvent.setVersion(source.getVersion() + 1);
			requestEvent.setVesselType(((VesselTypeEvent) trigger).getVesselType());
			return requestEvent;
		}

		logger.error("Tipo de evento no soportado");
		throw new InternalException(ExceptionType.INTERNAL_EXCEPTION);
	}

	/////////////////

	public static Event getEvent(Event source, String type, String exceptionType,
			Map<String, String> exceptionArguments) {

		EventError failedEvent = null;

		if (type.equals(VesselTypeEventTypes.CREATE_FAILED)) {

			logger.debug("No se pudo crear Vessel en la vista");
			failedEvent = new CreateVesselFailedEvent().buildFrom(source);
		}

		if (type.equals(VesselTypeEventTypes.UPDATE_FAILED)) {

			logger.debug("No se pudo modificar Vessel en la vista");
			failedEvent = new UpdateVesselFailedEvent().buildFrom(source);
		}

		if (type.equals(VesselTypeEventTypes.DELETE_FAILED)) {

			logger.debug("No se pudo eliminar Vessel de la vista");
			failedEvent = new DeleteVesselFailedEvent().buildFrom(source);
		}

		if (type.equals(VesselEventTypes.DELETE_CHECK_FAILED)) {

			logger.debug("Checkeo de eliminación fallido, el item está referenciado");
			failedEvent = new DeleteVesselCheckFailedEvent().buildFrom(source);
		}

		if (type.equals(VesselEventTypes.CREATE_CANCELLED)) {

			logger.debug("Enviando evento CreateVesselCancelledEvent para: " + source.getAggregateId());
			failedEvent = new CreateVesselCancelledEvent().buildFrom(source);
		}

		if (failedEvent != null) {

			failedEvent.setExceptionType(exceptionType);
			failedEvent.setArguments(exceptionArguments);
			return failedEvent;

		} else {
			logger.error("Tipo de evento no soportado");
			throw new InternalException(ExceptionType.INTERNAL_EXCEPTION);
		}
	}

	////////////////////

	public static Event getEvent(Event source, String type, VesselDTO vessel, String exceptionType,
			Map<String, String> exceptionArguments) {

		VesselCancelledEvent cancelledEvent = null;

		if (type.equals(VesselEventTypes.UPDATE_CANCELLED)) {

			logger.debug("Creando evento UpdateVesselCancelledEvent para: " + source.getAggregateId());
			cancelledEvent = new UpdateVesselCancelledEvent().buildFrom(source);
		}

		if (type.equals(VesselEventTypes.DELETE_CANCELLED)) {

			logger.debug("Creando evento DeleteVesselCancelledEvent para: " + source.getAggregateId());
			cancelledEvent = new DeleteVesselCancelledEvent().buildFrom(source);
		}

		if (cancelledEvent != null) {

			cancelledEvent.setVessel(vessel);
			cancelledEvent.setExceptionType(exceptionType);
			cancelledEvent.setArguments(exceptionArguments);
			return cancelledEvent;

		} else {

			logger.error("Tipo de evento no soportado");
			throw new InternalException(ExceptionType.INTERNAL_EXCEPTION);
		}
	}
}
