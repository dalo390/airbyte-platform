import { createPostgresDestinationViaApi, createPostgresSourceViaApi } from "@cy/commands/connection";
import { requestDeleteConnection, requestDeleteDestination, requestDeleteSource } from "commands/api";
import { Connection, Destination, Source } from "commands/api/types";
import { submitButtonClick } from "commands/common";
import { runDbQuery } from "commands/db/db";
import {
  createUsersTableQuery,
  dropUsersTableQuery,
  createDummyTablesQuery,
  dropDummyTablesQuery,
} from "commands/db/queries";
import {
  interceptCreateConnectionRequest,
  interceptDiscoverSchemaRequest,
  interceptGetSourceDefinitionsRequest,
  interceptGetSourcesListRequest,
  waitForCreateConnectionRequest,
  waitForDiscoverSchemaRequest,
  waitForGetSourceDefinitionsRequest,
  waitForGetSourcesListRequest,
} from "commands/interceptors";

import * as replicationPage from "pages/connection/connectionFormPageObject";
import * as connectionListPage from "pages/connection/connectionListPageObject";
import * as newConnectionPage from "pages/connection/createConnectionPageObject";
import { streamDetails } from "pages/connection/StreamDetailsPageObject";
import { StreamRowPageObject } from "pages/connection/StreamRowPageObject";
import { streamsTable } from "pages/connection/StreamsTablePageObject";

describe("Connection - Create new connection", { testIsolation: false }, () => {
  let source: Source;
  let destination: Destination;
  let connectionId: string;

  const dropTables = () => {
    runDbQuery(dropUsersTableQuery, dropDummyTablesQuery(20));
  };
  before(() => {
    dropTables();
    runDbQuery(createUsersTableQuery, createDummyTablesQuery(20));
    createPostgresSourceViaApi().then((pgSource) => {
      source = pgSource;
    });
    createPostgresDestinationViaApi().then((pgDestination) => {
      destination = pgDestination;
    });
  });

  after(() => {
    if (connectionId) {
      requestDeleteConnection(connectionId);
    }
    if (source) {
      requestDeleteSource(source.sourceId);
    }
    if (destination) {
      requestDeleteDestination(destination.destinationId);
    }

    dropTables();
  });

  describe.only("Set up source and destination", () => {
    // todo: switching back and forth between views for existing/new connectors
    describe("With existing connectors", () => {
      it("should open 'New connection' page", () => {
        connectionListPage.visit();
        interceptGetSourcesListRequest();
        interceptGetSourceDefinitionsRequest();

        connectionListPage.clickNewConnectionButton();
        waitForGetSourcesListRequest();
        waitForGetSourceDefinitionsRequest();
      });

      it("should select existing Source from dropdown and click button", () => {
        newConnectionPage.isExistingConnectorTypeSelected("source");
        newConnectionPage.selectExistingConnectorFromList("source", source.name);
      });

      it("should select existing Destination from dropdown and click button", () => {
        interceptDiscoverSchemaRequest();
        newConnectionPage.isExistingConnectorTypeSelected("destination");
        newConnectionPage.selectExistingConnectorFromList("destination", destination.name);
        waitForDiscoverSchemaRequest();
      });

      it("should redirect to 'New connection' configuration page with stream table'", () => {
        newConnectionPage.isAtNewConnectionPage();
      });
    });
  });

  describe("Configuration", () => {
    it("should set 'Replication frequency' to 'Manual'", () => {
      replicationPage.selectSchedule("Manual");
    });
  });

  describe("Streams table", () => {
    it("should check check connector icons and titles in table", () => {
      newConnectionPage.checkConnectorIconAndTitle("source");
      newConnectionPage.checkConnectorIconAndTitle("destination");
    });

    it("should check columns names in table", () => {
      newConnectionPage.checkColumnNames();
    });

    it("should check total amount of table streams", () => {
      // dummy tables amount + users table
      newConnectionPage.checkAmountOfStreamTableRows(21);
    });

    it("should allow to scroll table to desired stream table row and it should be visible", () => {
      const desiredStreamTableRow = "dummy_table_18";

      newConnectionPage.scrollTableToStream(desiredStreamTableRow);
      newConnectionPage.isStreamTableRowVisible(desiredStreamTableRow);
    });

    it("should filter table by stream name", () => {
      streamsTable.searchStream("dummy_table_10");
      newConnectionPage.checkAmountOfStreamTableRows(1);
    });

    it("should clear stream search input field and show all available streams", () => {
      streamsTable.clearStreamSearch();
      newConnectionPage.checkAmountOfStreamTableRows(21);
    });
  });

  describe("Stream", () => {
    const usersStreamRow = new StreamRowPageObject("public", "users");

    it("should have checked sync switch by default", () => {
      // filter table to have only one stream
      streamsTable.searchStream("users");
      newConnectionPage.checkAmountOfStreamTableRows(1);

      usersStreamRow.isStreamSyncEnabled(true);
    });

    it("should have unchecked sync switch after click", () => {
      usersStreamRow.toggleStreamSync();
      usersStreamRow.isStreamSyncEnabled(false);
    });

    it("should have removed stream style after click", () => {
      usersStreamRow.hasRemovedStyle(true);
    });

    it("should have checked sync switch after click and default stream style", () => {
      usersStreamRow.toggleStreamSync();
      usersStreamRow.isStreamSyncEnabled(true);
      usersStreamRow.hasRemovedStyle(false);
    });

    it("should have source namespace name", () => {
      usersStreamRow.checkSourceNamespace();
    });

    it("should have source stream name", () => {
      usersStreamRow.checkSourceStreamName();
    });

    // check sync mode by default - should be "Full Refresh | overwrite"
    // should have empty cursor field by default
    // should have empty primary key field by default
    // change default sync mode - stream row should have light blue background

    it("should have default destination namespace name", () => {
      usersStreamRow.checkDestinationNamespace("<destination schema>");
    });

    it("should have default destination stream name", () => {
      usersStreamRow.checkDestinationStreamName("users");
    });

    it("should open stream details panel by clicking on stream row", () => {
      usersStreamRow.showStreamDetails();
      streamDetails.isOpen();
    });

    it("should close stream details panel by clicking on close button", () => {
      streamDetails.close();
      streamDetails.isClosed();
    });
  });

  /*
    here will be added more tests to extend the test flow
   */

  describe("Submit form", () => {
    it("should set up a connection", () => {
      interceptCreateConnectionRequest();
      submitButtonClick(true);

      waitForCreateConnectionRequest().then((interception) => {
        assert.isNotNull(interception.response?.statusCode, "200");
        expect(interception.request.method).to.eq("POST");

        const connection: Partial<Connection> = {
          name: `${source.name} → ${destination.name}`,
          scheduleType: "manual",
        };
        expect(interception.request.body).to.contain(connection);
        expect(interception.response?.body).to.contain(connection);

        connectionId = interception.response?.body?.connectionId;
      });
    });

    it("should redirect to connection overview page after connection set up", () => {
      newConnectionPage.isAtConnectionOverviewPage(connectionId);
    });
  });
});
