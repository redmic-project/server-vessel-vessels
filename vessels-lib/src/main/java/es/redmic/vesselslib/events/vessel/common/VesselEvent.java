package es.redmic.vesselslib.events.vessel.common;

import org.apache.avro.Schema;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.fasterxml.jackson.annotation.JsonIgnore;

import es.redmic.brokerlib.avro.common.Event;
import es.redmic.vesselslib.dto.vessel.VesselDTO;

public abstract class VesselEvent extends Event {

	private VesselDTO vessel;

	public VesselEvent(String type) {
		super(type);
	}

	public VesselDTO getVessel() {
		return vessel;
	}

	public void setVessel(VesselDTO vessel) {
		this.vessel = vessel;
	}

	@JsonIgnore
	@Override
	public Object get(int field$) {
		switch (field$) {
		case 0:
			return getVessel();
		case 1:
			return getAggregateId();
		case 2:
			return getVersion();
		case 3:
			return getType();
		case 4:
			return getDate().getMillis();
		case 5:
			return getSessionId();
		case 6:
			return getUserId();
		case 7:
			return getId();
		default:
			throw new org.apache.avro.AvroRuntimeException("Bad index");
		}
	}

	@JsonIgnore
	@Override
	public void put(int field$, Object value$) {
		switch (field$) {
		case 0:
			vessel = (es.redmic.vesselslib.dto.vessel.VesselDTO) value$;
			break;
		case 1:
			setAggregateId(value$.toString());
			break;
		case 2:
			setVersion((java.lang.Integer) value$);
			break;
		case 3:
			setType(value$.toString());
			break;
		case 4:
			setDate(new DateTime(value$, DateTimeZone.UTC));
			break;
		case 5:
			setSessionId(value$.toString());
			break;
		case 6:
			setUserId(value$.toString());
			break;
		case 7:
			setId(value$.toString());
			break;
		default:
			throw new org.apache.avro.AvroRuntimeException("Bad index");
		}
	}

	@JsonIgnore
	public static String getVesselEventSchema() {

		return "{\"name\":\"vessel\", \"type\": " + VesselDTO.SCHEMA$.toString() + "}";
	}

	@JsonIgnore
	@Override
	public Schema getSchema() {
		return null;
	}
}
