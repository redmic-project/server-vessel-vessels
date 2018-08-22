package es.redmic.vesselscommands.handler;

import java.util.concurrent.CompletableFuture;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import es.redmic.brokerlib.alert.AlertService;
import es.redmic.commandslib.commands.CommandHandler;
import es.redmic.commandslib.streaming.common.StreamConfig;
import es.redmic.commandslib.streaming.common.StreamConfig.Builder;
import es.redmic.exception.factory.ExceptionFactory;
import es.redmic.vesselscommands.aggregate.VesselAggregate;
import es.redmic.vesselscommands.commands.vessel.CreateVesselCommand;
import es.redmic.vesselscommands.commands.vessel.DeleteVesselCommand;
import es.redmic.vesselscommands.commands.vessel.UpdateVesselCommand;
import es.redmic.vesselscommands.config.UserService;
import es.redmic.vesselscommands.statestore.VesselStateStore;
import es.redmic.vesselscommands.streams.VesselEventStreams;
import es.redmic.vesselslib.dto.vessel.VesselDTO;
import es.redmic.vesselslib.events.vessel.create.CreateVesselCancelledEvent;
import es.redmic.vesselslib.events.vessel.create.CreateVesselEvent;
import es.redmic.vesselslib.events.vessel.create.CreateVesselFailedEvent;
import es.redmic.vesselslib.events.vessel.create.VesselCreatedEvent;
import es.redmic.vesselslib.events.vessel.delete.DeleteVesselCancelledEvent;
import es.redmic.vesselslib.events.vessel.delete.DeleteVesselConfirmedEvent;
import es.redmic.vesselslib.events.vessel.delete.DeleteVesselEvent;
import es.redmic.vesselslib.events.vessel.delete.VesselDeletedEvent;
import es.redmic.vesselslib.events.vessel.update.UpdateVesselCancelledEvent;
import es.redmic.vesselslib.events.vessel.update.UpdateVesselEvent;
import es.redmic.vesselslib.events.vessel.update.VesselUpdatedEvent;

@Component
@KafkaListener(topics = "${broker.topic.vessel}")
public class VesselCommandHandler extends CommandHandler {

	@Value("${spring.kafka.properties.schema.registry.url}")
	private String schemaRegistry;

	@Value("${spring.kafka.bootstrap-servers}")
	private String bootstrapServers;

	@Value("${broker.topic.vessel}")
	private String vesselTopic;

	@Value("${broker.topic.vessels.agg.by.vesseltype}")
	private String vesselsAggByVesselTypeTopic;

	@Value("${broker.state.store.vessels.dir}")
	private String stateStoreVesselsDir;

	@Value("${broker.state.store.vessels.id}")
	private String vesselsIdConfig;

	@Value("${broker.stream.events.vessels.id}")
	private String vesselsEventsStreamId;

	@Value("${broker.topic.vessel-type}")
	private String vesselTypeTopic;

	@Value("${stream.windows.time.ms}")
	private Long streamWindowsTime;

	@Value("${process.eventsource.timeout.ms}")
	private long processTimeoutMS;

	private final String REDMIC_PROCESS = "REDMIC_PROCESS";

	private VesselStateStore vesselStateStore;

	@Autowired
	VesselTypeCommandHandler vesselTypeCommandHandler;

	@Autowired
	UserService userService;

	@Autowired
	AlertService alertService;

	public VesselCommandHandler() {
	}

	@PostConstruct
	private void setUp() {

		// @formatter:off
		
		Builder config = StreamConfig.Builder
			.bootstrapServers(bootstrapServers)
			.schemaRegistry(schemaRegistry)
			.stateStoreDir(stateStoreVesselsDir)
			.topic(vesselTopic);
		
		vesselStateStore = new VesselStateStore(
				config
					.serviceId(vesselsIdConfig)
					.build(), alertService);

		new VesselEventStreams(
				config
					.serviceId(vesselsEventsStreamId)
					.windowsTime(streamWindowsTime)
					.build(), vesselTypeTopic, vesselsAggByVesselTypeTopic, alertService);
		// @formatter:on
	}

	public VesselDTO save(CreateVesselCommand cmd) {

		VesselAggregate agg = new VesselAggregate(vesselStateStore);

		logger.debug("Procesando CreateVesselCommand");

		// Rellena el vesselType. Si es null lo descartamos
		if (cmd.getVessel().getType() != null)
			cmd.getVessel().setType(vesselTypeCommandHandler.getVesselType(cmd.getVessel().getType()));
		else // TODO: enviar correo a operador de datos.
			logger.warn("Vessel con id " + cmd.getVessel().getId() + " no tiene definido el tipo.");

		// Se procesa el comando, obteniendo el evento generado
		CreateVesselEvent event = agg.process(cmd);

		// Si no se genera evento significa que no se debe aplicar
		if (event == null) {
			return null;
		}

		event.setUserId(userService.getUserId());

		// Se aplica el evento
		agg.apply(event);

		// Crea la espera hasta que se responda con evento completado
		CompletableFuture<VesselDTO> completableFuture = getCompletableFeature(event.getSessionId(), agg.getVessel());

		// Emite evento para enviar a kafka
		publishToKafka(event, vesselTopic);

		// Se resuelve con un timeout mayor, establecido para procesos automáticos
		if (event.getUserId().equals(REDMIC_PROCESS))
			return getResult(processTimeoutMS, event.getSessionId(), completableFuture);

		// Obtiene el resultado cuando se resuelva la espera
		return getResult(event.getSessionId(), completableFuture);
	}

	public VesselDTO update(String id, UpdateVesselCommand cmd) {

		VesselAggregate agg = new VesselAggregate(vesselStateStore);

		// El vesselType es obligado
		cmd.getVessel().setType(vesselTypeCommandHandler.getVesselType(cmd.getVessel().getType()));

		// Se procesa el comando, obteniendo el evento generado
		UpdateVesselEvent event = agg.process(cmd);

		// Si no se genera evento significa que no se va a aplicar
		if (event == null)
			return null;

		event.setUserId(userService.getUserId());

		// Si no existen excepciones, se aplica el comando
		agg.apply(event);

		// Crea la espera hasta que se responda con evento completado
		CompletableFuture<VesselDTO> completableFuture = getCompletableFeature(event.getSessionId(), agg.getVessel());

		// Emite evento para enviar a kafka
		publishToKafka(event, vesselTopic);

		// Obtiene el resultado cuando se resuelva la espera
		return getResult(event.getSessionId(), completableFuture);
	}

	public VesselDTO update(String id, DeleteVesselCommand cmd) {

		VesselAggregate agg = new VesselAggregate(vesselStateStore);
		agg.setAggregateId(id);

		// Se procesa el comando, obteniendo el evento generado
		DeleteVesselEvent event = agg.process(cmd);

		// Si no se genera evento significa que no se va a aplicar
		if (event == null)
			return null;

		event.setUserId(userService.getUserId());

		// Si no existen excepciones, se aplica el comando
		agg.apply(event);

		// Crea la espera hasta que se responda con evento completado
		CompletableFuture<VesselDTO> completableFuture = getCompletableFeature(event.getSessionId(), agg.getVessel());

		// Emite evento para enviar a kafka
		publishToKafka(event, vesselTopic);

		// Obtiene el resultado cuando se resuelva la espera
		return getResult(event.getSessionId(), completableFuture);
	}

	public VesselDTO getVessel(VesselDTO type) {

		VesselAggregate vesselAggregate = new VesselAggregate(vesselStateStore);

		return vesselAggregate.getVesselFromStateStore(type);
	}

	@KafkaHandler
	private void listen(VesselCreatedEvent event) {

		logger.info("Vessel creado " + event.getAggregateId());

		// El evento Creado se envió desde el stream

		resolveCommand(event.getSessionId());
	}

	@KafkaHandler
	private void listen(VesselUpdatedEvent event) {

		logger.info("Vessel modificado " + event.getAggregateId());

		// El evento Modificado se envió desde el stream

		resolveCommand(event.getSessionId());
	}

	@KafkaHandler
	private void listen(DeleteVesselConfirmedEvent event) {

		logger.info("Enviando evento VesselDeletedEvent para: " + event.getAggregateId());

		publishToKafka(new VesselDeletedEvent().buildFrom(event), vesselTopic);
	}

	@KafkaHandler
	private void listen(VesselDeletedEvent event) {

		logger.info("Vessel eliminado " + event.getAggregateId());

		resolveCommand(event.getSessionId());
	}

	@KafkaHandler
	private void listen(CreateVesselFailedEvent event) {

		logger.info("Enviando evento CreateVesselCancelEvent para: " + event.getAggregateId());

		CreateVesselCancelledEvent evt = new CreateVesselCancelledEvent().buildFrom(event);
		evt.setExceptionType(event.getExceptionType());
		evt.setArguments(event.getArguments());

		publishToKafka(evt, vesselTopic);
	}

	@KafkaHandler
	private void listen(CreateVesselCancelledEvent event) {

		logger.info("Error creando Vessel " + event.getAggregateId());

		resolveCommand(event.getSessionId(),
				ExceptionFactory.getException(event.getExceptionType(), event.getArguments()));
	}

	@KafkaHandler
	private void listen(UpdateVesselCancelledEvent event) {

		logger.info("Error modificando Vessel " + event.getAggregateId());

		// El evento Cancelled se envía desde el stream

		resolveCommand(event.getSessionId(),
				ExceptionFactory.getException(event.getExceptionType(), event.getArguments()));
	}

	@KafkaHandler
	private void listen(DeleteVesselCancelledEvent event) {

		logger.info("Error eliminando Vessel " + event.getAggregateId());

		// El evento Cancelled se envía desde el stream

		resolveCommand(event.getSessionId(),
				ExceptionFactory.getException(event.getExceptionType(), event.getArguments()));
	}
}
