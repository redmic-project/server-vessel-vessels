package es.redmic.vesselslib.events.vessel.delete;

import org.apache.avro.Schema;

import es.redmic.brokerlib.avro.common.SimpleEvent;
import es.redmic.vesselslib.events.vessel.VesselEventTypes;

public class CheckDeleteVesselEvent extends SimpleEvent {

	// @formatter:off

	public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{"
		+ "\"type\":\"record\",\"name\":\"CheckDeleteVesselEvent\","
				+ "\"namespace\":\"es.redmic.vesselslib.events.vessel.delete\",\"fields\":["
			+ getEventBaseSchema() + "]}");
	// @formatter:on

	static String type = VesselEventTypes.CHECK_DELETE;

	public CheckDeleteVesselEvent() {
		super(type);
	}

	@Override
	public Schema getSchema() {
		return SCHEMA$;
	}
}
