package es.redmic.test.vesselscommands.unit.aggregate.vesseltype;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import es.redmic.commandslib.exceptions.ItemLockedException;
import es.redmic.exception.data.ItemNotFoundException;
import es.redmic.vesselscommands.commands.vesseltype.CreateVesselTypeCommand;
import es.redmic.vesselscommands.commands.vesseltype.DeleteVesselTypeCommand;
import es.redmic.vesselscommands.commands.vesseltype.UpdateVesselTypeCommand;
import es.redmic.vesselslib.dto.vesseltype.VesselTypeDTO;
import es.redmic.vesselslib.events.vesseltype.VesselTypeEventTypes;
import es.redmic.vesselslib.events.vesseltype.create.CreateVesselTypeEvent;
import es.redmic.vesselslib.events.vesseltype.delete.CheckDeleteVesselTypeEvent;
import es.redmic.vesselslib.events.vesseltype.update.UpdateVesselTypeEvent;

@RunWith(MockitoJUnitRunner.class)
public class ProcessEventTest extends AggregateBaseTest {

	@Test
	public void processCreateVesselTypeCommand_ReturnVesselTypeCreatedEvent_IfProcessIsOk() {

		when(vesselsTypeStateStore.getVesselType(any())).thenReturn(null);

		VesselTypeDTO vessel = getVesselType();

		CreateVesselTypeCommand command = new CreateVesselTypeCommand(vessel);

		CreateVesselTypeEvent evt = agg.process(command);

		assertNotNull(evt);
		assertNotNull(evt.getDate());
		assertNotNull(evt.getVesselType());
		assertEquals(evt.getVesselType(), vessel);
		assertNotNull(evt.getId());
		assertEquals(evt.getAggregateId(), vessel.getId());
		assertEquals(evt.getType(), VesselTypeEventTypes.CREATE);
		assertTrue(evt.getVersion().equals(1));
	}

	@Test
	public void processUpdateVesselTypeCommand_ReturnVesselTypeUpdatedEvent_IfProcessIsOk() {

		when(vesselsTypeStateStore.getVesselType(any())).thenReturn(getVesselTypeCreatedEvent());

		VesselTypeDTO vessel = getVesselType();

		UpdateVesselTypeCommand command = new UpdateVesselTypeCommand(vessel);

		UpdateVesselTypeEvent evt = agg.process(command);

		assertNotNull(evt);
		assertNotNull(evt.getDate());
		assertNotNull(evt.getVesselType());
		assertEquals(evt.getVesselType(), vessel);
		assertNotNull(evt.getId());
		assertEquals(evt.getAggregateId(), vessel.getId());
		assertEquals(evt.getType(), VesselTypeEventTypes.UPDATE);
		assertTrue(evt.getVersion().equals(2));
	}

	// Editar un elemento ya borrado
	@Test(expected = ItemNotFoundException.class)
	public void processUpdateVesselCommand_ThrowItemNotFoundException_IfItemIsDeleted() {

		when(vesselsTypeStateStore.getVesselType(any())).thenReturn(getVesselTypeDeletedEvent());

		VesselTypeDTO vessel = getVesselType();

		agg.process(new UpdateVesselTypeCommand(vessel));
	}

	// Editar un elemento bloqueado
	@Test(expected = ItemLockedException.class)
	public void processUpdateVesselCommand_ThrowItemLockedException_IfItemIsLocked() {

		when(vesselsTypeStateStore.getVesselType(any())).thenReturn(getUpdateVesselTypeEvent());

		VesselTypeDTO vessel = getVesselType();

		agg.process(new UpdateVesselTypeCommand(vessel));
	}

	@Test
	public void processDeleteVesselTypeCommand_ReturnCheckDeleteVesselTypeEvent_IfProcessIsOk() {

		when(vesselsTypeStateStore.getVesselType(any())).thenReturn(getVesselTypeUpdatedEvent());

		VesselTypeDTO vessel = getVesselType();

		DeleteVesselTypeCommand command = new DeleteVesselTypeCommand(vessel.getId());

		CheckDeleteVesselTypeEvent evt = agg.process(command);

		assertNotNull(evt);
		assertNotNull(evt.getDate());
		assertNotNull(evt.getId());
		assertEquals(evt.getAggregateId(), vessel.getId());
		assertEquals(evt.getType(), VesselTypeEventTypes.CHECK_DELETE);
		assertTrue(evt.getVersion().equals(3));
	}

	// Borrar un elemento ya borrado
	@Test(expected = ItemNotFoundException.class)
	public void processDeleteVesselCommand_ThrowItemNotFoundException_IfItemIsDeleted() {

		when(vesselsTypeStateStore.getVesselType(any())).thenReturn(getVesselTypeDeletedEvent());

		VesselTypeDTO vessel = getVesselType();

		agg.process(new DeleteVesselTypeCommand(vessel.getId()));
	}

	// Borrar un elemento bloqueado
	@Test(expected = ItemLockedException.class)
	public void processDeleteVesselCommand_ThrowItemLockedException_IfItemIsLocked() {

		when(vesselsTypeStateStore.getVesselType(any())).thenReturn(getUpdateVesselTypeEvent());

		VesselTypeDTO vessel = getVesselType();

		agg.process(new DeleteVesselTypeCommand(vessel.getId()));
	}
}
