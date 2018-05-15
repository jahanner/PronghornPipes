package com.ociweb.json.parse;

import com.ociweb.json.JSONType;
import com.ociweb.json.decode.JSONExtractor;
import com.ociweb.json.encode.JSONRenderer;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.util.AppendableByteWriter;

public class JSONResponse {
    private int status = 0;
    private final StringBuilder message = new StringBuilder();
    private final StringBuilder body = new StringBuilder();

    private static final JSONRenderer<JSONResponse> jsonRenderer = new JSONRenderer<JSONResponse>()
            .beginObject()
            .integer("status", o->o.status)
            .string("message", o->o.message)
            .string("body", o->o.body)
            .endObject();

    public enum Fields {
    	Status, Message, Body;
    }    

	private final JSONExtractor jsonExtractor = new JSONExtractor()
			.begin()
				.element(JSONType.TypeInteger, true)
				.asField("status",Fields.Status)
				
				.element(JSONType.TypeString, true)//set flags for first, last, all, ordered...
				.asField("message",Fields.Message)
				
				.element(JSONType.TypeString, true)//set flags for first, last, all, ordered...
				.asField("body",Fields.Body)
			.finish();
    
    public void reset() {
        status = 0;
        message.setLength(0);
        this.message.setLength(0);
        body.setLength(0);
    }

    public void setStatusMessage(StatusMessages statusMessage) {
        this.status = statusMessage.getStatusCode();
        this.message.append(statusMessage.getStatusMessage());
    }

    public int getStatus() { return status; }

    public String getMessage() {
        return message.toString();
    }

    public String getBody() {
        return body.toString();
    }

    public void setBody(String body) {
        this.body.append(body);
    }

    public boolean readFromJSON(ChannelReader reader) {
    	
    	status = reader.structured().readInt(Fields.Status);
    	reader.structured().readText(Fields.Message, message);
    	reader.structured().readText(Fields.Body, body);
    	
        return true;
    }

    public void writeToJSON(AppendableByteWriter writer) {
        jsonRenderer.render(writer, this);
    }

    public enum StatusMessages {
        SUCCESS(200, "Success"),
        FAILURE(500, "Server Error"),
        BAD_REQUEST(400, "Bad Request");

        private final int statusCode;
        private final String statusMessage;

        StatusMessages(int statusCode, String statusMessage) {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getStatusMessage() {
            return statusMessage;
        }
    }
}
