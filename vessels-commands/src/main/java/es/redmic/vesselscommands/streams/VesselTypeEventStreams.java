package es.redmic.vesselscommands.streams;

/*-
 * #%L
 * Vessels-management
 * %%
 * Copyright (C) 2019 REDMIC Project / Server
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.HashMap;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;

import es.redmic.brokerlib.alert.AlertService;
import es.redmic.brokerlib.avro.common.Event;
import es.redmic.brokerlib.avro.common.EventError;
import es.redmic.brokerlib.avro.common.EventTypes;
import es.redmic.brokerlib.avro.serde.hashmap.HashMapSerde;
import es.redmic.commandslib.exceptions.ExceptionType;
import es.redmic.commandslib.streaming.common.StreamConfig;
import es.redmic.commandslib.streaming.streams.EventSourcingStreams;
import es.redmic.vesselslib.dto.vesseltype.VesselTypeDTO;
import es.redmic.vesselslib.events.vessel.partialupdate.vesseltype.AggregationVesselTypeInVesselPostUpdateEvent;
import es.redmic.vesselslib.events.vesseltype.VesselTypeEventFactory;
import es.redmic.vesselslib.events.vesseltype.VesselTypeEventTypes;
import es.redmic.vesselslib.events.vesseltype.common.VesselTypeEvent;

public class VesselTypeEventStreams extends EventSourcingStreams {

	private String vesselsAggByVesselTypeTopic;

	private HashMapSerde<String, AggregationVesselTypeInVesselPostUpdateEvent> hashMapSerde;

	private KTable<String, HashMap<String, AggregationVesselTypeInVesselPostUpdateEvent>> aggByVesselType;

	public VesselTypeEventStreams(StreamConfig config, String vesselsAggByVesselTypeTopic, AlertService alertService) {
		super(config, alertService);
		this.vesselsAggByVesselTypeTopic = vesselsAggByVesselTypeTopic;
		this.hashMapSerde = new HashMapSerde<>(schemaRegistry);

		init();
	}

	/**
	 * Crea KTable de vessels agregados por vesseltype
	 * 
	 * @see es.redmic.commandslib.streaming.streams.EventSourcingStreams#
	 *      createExtraStreams()
	 */

	@Override
	protected void createExtraStreams() {
		aggByVesselType = builder.table(vesselsAggByVesselTypeTopic, Consumed.with(Serdes.String(), hashMapSerde));
	}

	/**
	 * Reenvía eventos finales a topic de snapshot
	 */
	@Override
	protected void forwardSnapshotEvents(KStream<String, Event> events) {

		events.filter((id, event) -> (VesselTypeEventTypes.isSnapshot(event.getType()))).to(snapshotTopic);
	}

	/**
	 * Función que apartir del evento de confirmación de la vista y del evento
	 * create (petición de creación), si todo es correcto, genera evento created
	 */

	@Override
	protected Event getCreatedEvent(Event confirmedEvent, Event requestEvent) {

		assert requestEvent.getType().equals(VesselTypeEventTypes.CREATE);

		assert confirmedEvent.getType().equals(VesselTypeEventTypes.CREATE_CONFIRMED);

		if (!isSameSession(confirmedEvent, requestEvent)) {
			return null;
		}

		VesselTypeDTO vesselType = ((VesselTypeEvent) requestEvent).getVesselType();

		return VesselTypeEventFactory.getEvent(confirmedEvent, VesselTypeEventTypes.CREATED, vesselType);
	}

	/**
	 * Función que apartir del evento de confirmación de la vista y del evento
	 * update (petición de modificación), si todo es correcto, genera evento updated
	 */

	@Override
	protected Event getUpdatedEvent(Event confirmedEvent, Event requestEvent) {

		assert requestEvent.getType().equals(VesselTypeEventTypes.UPDATE);

		assert confirmedEvent.getType().equals(VesselTypeEventTypes.UPDATE_CONFIRMED);

		if (!isSameSession(confirmedEvent, requestEvent)) {
			return null;
		}

		VesselTypeDTO vesselType = ((VesselTypeEvent) requestEvent).getVesselType();

		return VesselTypeEventFactory.getEvent(requestEvent, VesselTypeEventTypes.UPDATED, vesselType);
	}

	/**
	 * Comprueba si vesselType está referenciado en vessel para cancelar el borrado
	 */

	@Override
	protected void processDeleteStream(KStream<String, Event> events) {

		// Stream filtrado por eventos de borrado
		KStream<String, Event> deleteEvents = events
				.filter((id, event) -> (EventTypes.CHECK_DELETE.equals(event.getType())));

		deleteEvents.leftJoin(aggByVesselType,
				(deleteEvent, vesselAggByVesselType) -> getCheckDeleteResultEvent(deleteEvent, vesselAggByVesselType))
				.to(topic);
	}

	@SuppressWarnings("serial")
	private Event getCheckDeleteResultEvent(Event deleteEvent,
			HashMap<String, AggregationVesselTypeInVesselPostUpdateEvent> vesselAggByVesselType) {

		if (vesselAggByVesselType == null || vesselAggByVesselType.isEmpty()) { // elemento no referenciado

			return VesselTypeEventFactory.getEvent(deleteEvent, VesselTypeEventTypes.DELETE_CHECKED);
		} else { // elemento referenciado

			return VesselTypeEventFactory.getEvent(deleteEvent, VesselTypeEventTypes.DELETE_CHECK_FAILED,
					ExceptionType.ITEM_REFERENCED.toString(), new HashMap<String, String>() {
						{
							put("id", deleteEvent.getAggregateId());
						}
					});
		}
	}

	/**
	 * Función que a partir del evento fallido y el último evento correcto, genera
	 * evento UpdateCancelled
	 */

	@Override
	protected Event getUpdateCancelledEvent(Event failedEvent, Event lastSuccessEvent) {

		assert failedEvent.getType().equals(VesselTypeEventTypes.UPDATE_FAILED);

		assert VesselTypeEventTypes.isSnapshot(lastSuccessEvent.getType());

		VesselTypeDTO vesselType = ((VesselTypeEvent) lastSuccessEvent).getVesselType();

		EventError eventError = (EventError) failedEvent;

		return VesselTypeEventFactory.getEvent(failedEvent, VesselTypeEventTypes.UPDATE_CANCELLED, vesselType,
				eventError.getExceptionType(), eventError.getArguments());
	}

	/**
	 * Función que a partir del evento fallido y el último evento correcto, genera
	 * evento DeleteFailed
	 */

	@Override
	protected Event getDeleteCancelledEvent(Event failedEvent, Event lastSuccessEvent) {

		assert failedEvent.getType().equals(VesselTypeEventTypes.DELETE_FAILED);

		assert VesselTypeEventTypes.isSnapshot(lastSuccessEvent.getType());

		VesselTypeDTO vesselType = ((VesselTypeEvent) lastSuccessEvent).getVesselType();

		EventError eventError = (EventError) failedEvent;

		return VesselTypeEventFactory.getEvent(failedEvent, VesselTypeEventTypes.DELETE_CANCELLED, vesselType,
				eventError.getExceptionType(), eventError.getArguments());
	}

	@Override
	protected void processEnrichCreateSteam(KStream<String, Event> events) {
		// En este caso no hay enriquecimiento
	}

	@Override
	protected void processEnrichUpdateSteam(KStream<String, Event> events) {
		// En este caso no hay enriquecimiento
	}

	@Override
	protected void processPartialUpdatedStream(KStream<String, Event> vesselTypeEvents,
			KStream<String, Event> updateConfirmedEvents) {
		// En este caso no hay modificaciones parciales
	}

	/**
	 * Función para procesar modificaciones de referencias
	 */

	@Override
	protected void processPostUpdateStream(KStream<String, Event> events) {
		// En este caso no hay modificación de relaciones
	}

	@Override
	protected void processExtraStreams(KStream<String, Event> events, KStream<String, Event> snapshotEvents) {
	}
}
