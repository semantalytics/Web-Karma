/*******************************************************************************
 * Copyright 2012 University of Southern California
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This code was developed by the Information Integration Group as part
 * of the Karma project at the Information Sciences Institute of the
 * University of Southern California.  For more information, publications,
 * and related projects, please see: http://www.isi.edu/integration
 ******************************************************************************/

function parse(data) {
    $.workspaceGlobalInformation.id = data["workspaceId"];

    var isError = false;
    var error = [];
    var infos = [];
    var trivialErrors = [];
    
    // Check for errors
    $.each(data["elements"], function(i, element) {
    	if(element["updateType"] == "ReloadPageUpdate") {
    		//Need to reload the page
    		location.reload();
    	}
        if(element["updateType"] == "KarmaError") {
        	if(error[element["Error"]]) {
        		//ignore;
        	} else {
	            $.sticky("<span class='karmaError'>" + element["Error"] + "</span>");
	            isError = true;
	            error[element["Error"]] = true;
        	}
        }
    });

    if(isError)
    	return false;
    
    /* Always add the charts from cleaning service in end, so pushing that CleaningServiceUpdate in the end of updates array (if present) */
    // Identify the index
    var cleaningUpdates = new Array();
    var dataElements = new Array();
    $.each(data["elements"], function(i, element) {
    	if(element["updateType"] == "UISettings") {
    		$.workspaceGlobalInformation.UISettings = element["settings"];
    	} else if(element["updateType"] == "WorksheetCleaningUpdate") {
        	cleaningUpdates.push(element);
        } else {
        	dataElements.push(element);
        }
    });
    data["elements"] = dataElements;
    // Move cleaning updates to the end
    $.each(cleaningUpdates, function(i, update) {
    	data["elements"].push(update);
    });
   

    // Loop through each update from the server and take required action for the GUI
    $.each(data["elements"], function(i, element) {
        if(element["updateType"] == "WorksheetListUpdate") {

            $.each(element["worksheets"], function(j, worksheet) {
                // If worksheet doesn't exist yet
                if($("div#" + worksheet["worksheetId"]).length == 0) {
                    var mainDiv = $("<div>").attr("id", worksheet["worksheetId"]).addClass("Worksheet");
                    mainDiv.data("isCollapsed", worksheet["isCollapsed"]);

                    // Div for adding title of that worksheet
                    var titleDiv = $("<div>").addClass("WorksheetTitleDiv ui-corner-top").mouseleave(function() {
                        // Hiding the option buttons for the heading and the data cell
                        $("div#tableCellMenuButtonDiv").hide();
                        $("div#columnHeadingMenuButtonDiv").hide();
                    });

                    var mapDiv = $("<div>").addClass("toggleMapView");
                    if(googleEarthEnabled) {
                    	mapDiv
                    		.append($("<img>")
	                            .attr("src", "images/google-earth-32.png"))
	                        .qtip({
	                            content : {
	                                text : 'View on map'
	                            },
	                            position : {
	                                my : 'top center', // Position my top left...
	                                at : 'bottom center', // at the bottom right of...
	                            },
	                            style : {
	                                classes : 'ui-tooltip-light ui-tooltip-shadow'
	                            }
	                        })
	                        .data("worksheetId", worksheet["worksheetId"])
	                        .data("state", "table")
	                        .click(showMapViewForWorksheet);
                    }
                    
                    titleDiv
                        .append((new WorksheetOptions(worksheet["worksheetId"], worksheet["title"])).generateJS())
                        .append($("<div>")
                            .addClass("rightOptionsToolbar")
                            .append($("<div>")
                                .addClass("showEncoding")
                                .text(worksheet["encoding"]))
                            .append(mapDiv)
                            .append($("<div>")
                                .addClass("showHideWorkSheet")
                                .addClass("glyphicon")
                                .addClass("glyphicon-chevron-up")		
                                .attr("id", "hideShow" + worksheet["worksheetId"])
                                .click(function() {
                                    $("div.svg-model", mainDiv).toggle(400);
                                    $("div.table-container", mainDiv).toggle(400);
                                    $("div.table-data-container", mainDiv).toggle(400);

                                    // Change the corners
                                    titleDiv.toggleClass("ui-corner-top");
                                    titleDiv.toggleClass("ui-corner-all");

                                    // Change the icon
                                    if($(this).hasClass("glyphicon-chevron-up")) {
                                    	$(this).removeClass("glyphicon-chevron-up");
                                    	$(this).addClass("glyphicon-chevron-down");
                                    } else {
                                    	$(this).addClass("glyphicon-chevron-up");
                                    	$(this).removeClass("glyphicon-chevron-down");
                                    }
                                })
                            )
                        );
                    mainDiv.append(titleDiv);

                    // Add the table (if it does not exists)
                    var tableDiv = $("<div>").attr("id", worksheet["worksheetId"] + "TableDiv").addClass("TableDiv").mouseleave(function() {
                        $("div#tableCellMenuButtonDiv").hide();
                        $("div#columnHeadingMenuButtonDiv").hide();
                    });

                    $("#tablesWorkspace").append(mainDiv).append("<br>");
                } else {

                }
            });
        }
        else if(element["updateType"] == "WorksheetDeleteUpdate") {
        	var worksheetPanel = $("div.Worksheet#" + element["worksheetId"]);
        	worksheetPanel.remove();
            $.sticky("Worksheet deleted");
        }
        else if(element["updateType"] == "WorksheetHeadersUpdate") {
            var worksheetPanel = $("div.Worksheet#" + element["worksheetId"]);

            var tableContainer = $("div.table-container", worksheetPanel);
            if (tableContainer.length == 0) {
                tableContainer = $("<div>").addClass("table-container");
                worksheetPanel.append(tableContainer);
            }

            var tableHeaderContainer = $("div.table-header-container", worksheetPanel);
            if (tableHeaderContainer.length == 0) {
                tableHeaderContainer = $("<div>").addClass("table-header-container");
                tableContainer.append(tableHeaderContainer);
            }

            var headersTable = $("table.wk-table", tableHeaderContainer);
            if (headersTable.length == 0) {
                headersTable = $("<table>").addClass("wk-table htable-odd");
                tableHeaderContainer.append(headersTable);
            } else {
                $("tr", headersTable).addClass("deleteMe");
            }

            var colWidths = addColumnHeadersRecurse(element["worksheetId"], element["columns"], headersTable, true);
            var stylesheet = document.styleSheets[0];

            // Remove the previous rows if any
            $("tr.deleteMe", headersTable).remove();

            $.each(colWidths, function(index2, colWidth){
                var selector = "." + colWidth.columnClass;
                var rule = "{width : " +colWidth.width+ "px }";
                if (stylesheet.insertRule) {
                    stylesheet.insertRule(selector + rule, stylesheet.cssRules.length);
                } else if (stylesheet.addRule) {
                    stylesheet.addRule(selector, rule, -1);
                }
            });
        }
        else if(element["updateType"] == "WorksheetDataUpdate") {
            var worksheetPanel = $("div.Worksheet#" + element["worksheetId"]);

            var tableDataContainer = $(worksheetPanel).children("div.table-data-container");
            if (tableDataContainer.length == 0) {
                tableDataContainer = $("<div>").addClass("table-data-container").addClass("table-data-container-outermost").attr("id", element["tableId"]);
                worksheetPanel.append(tableDataContainer);
            }

            var dataTable = $(tableDataContainer).children("table.wk-table");
            if (dataTable.length == 0) {
                dataTable = $("<table>").addClass("wk-table");
                tableDataContainer.append(dataTable);
            }

            // Check if the table has tbody for data rows. if not, then create one.
            var tBody = $(dataTable).children("tbody");
            if(tBody.length != 0) {
                // Mark the rows that need to be deleted later
                if($(tBody).children("tr").length != 0) {
                    $(tBody).children("tr").addClass("deleteMe");
                }
            }

            addWorksheetDataRecurse(element["worksheetId"], element["rows"], dataTable, true);

            // Delete the old rows
            $(tBody).children("tr.deleteMe").remove();

            var additionalRowsAvail = element["additionalRowsCount"];
            var moreRowsDiv = $("<div>").addClass("load-more");
            if (additionalRowsAvail == 0) {
                // Do nothing
            } else {
                // Remove any existing rows available links
                $("div.load-more", tableDataContainer).remove();
                var moreRowsLink = $("<a>").attr("href","#!").text(additionalRowsAvail + " additional records, load more...")
                    .click(loadAdditionalRowsHandler);
                moreRowsDiv.append(moreRowsLink);
                tableDataContainer.append(moreRowsDiv);
            }
        }
        else if(element["updateType"] == "HistoryAddCommandUpdate") {
        	var title = element.command.title;
        	if(element.command.description.length > 0) {
        		title = title + ": " + element.command.description;
        	}
            var commandDiv = $("<div>").addClass("CommandDiv undo-state " + element.command.commandType).attr("id", element.command.commandId).css({
                "position" : "relative"
            }).append($("<div>").text(title)).append($("<div>").addClass("iconDiv").append($("<img>").attr("src", "images/edit_undo.png")).bind('click', clickUndoButton).qtip({
                    content : {
                        text : 'Undo'
                    },
                    style : {
                        classes : 'ui-tooltip-light ui-tooltip-shadow'
                    }
                })).hover(
                    // hover in function
                    commandDivHoverIn,
                    // hover out function
                    commandDivHoverOut);
            if(element.command["commandType"] == "notUndoable")
                $("div.iconDiv", commandDiv).remove();
            var commandHistoryDiv = $("div#commandHistory");
            // Remove the commands on redo stack
            $(".redo-state").remove();

            commandHistoryDiv.append(commandDiv);
        }
        else if(element["updateType"] == "HistoryUpdate") {
            $("div#commandHistory div.CommandDiv").remove();
            $.each(element["commands"], function(index, command) {
                var commandDiv = $("<div>").addClass("CommandDiv " + command.commandType).attr("id", command.commandId).css({
                    "position" : "relative"
                }).append($("<div>").text(command.title + ": " + command.description)).append($("<div>").addClass("iconDiv").bind('click', clickUndoButton)).hover(
                        // hover in function
                        commandDivHoverIn,
                        // hover out function
                        commandDivHoverOut);
                if(command["commandType"] == "notUndoable")
                    $("div.iconDiv", commandDiv).remove();

                if(command.historyType == "redo") {
                    $(commandDiv).addClass("redo-state");
                    $("div.iconDiv", commandDiv).append($("<img>").attr("src", "images/edit_redo.png")).qtip({
                        content : {
                            text : 'Redo'
                        },
                        style : {
                            classes : 'ui-tooltip-light ui-tooltip-shadow'
                        }
                    });
                } else {
                    $(commandDiv).addClass("undo-state");
                    $("div.iconDiv", commandDiv).append($("<img>").attr("src", "images/edit_undo.png")).qtip({
                        content : {
                            text : 'Undo'
                        },
                        style : {
                            classes : 'ui-tooltip-light ui-tooltip-shadow'
                        }
                    });
                    ;
                }
                $("div#commandHistory").append(commandDiv);
            });
        }
        else if(element["updateType"] == "NodeChangedUpdate") {
            var cellDiv = $("div#" + element.nodeId);
            $(cellDiv).text(element.displayValue);

            $.removeData($(cellDiv), 'expandedValue');

            cellDiv.data("expandedValue", element["expandedValue"]);


            if(element.newStatus == "E") {
                cellDiv.addClass("editedValue");
            } else {
                cellDiv.removeClass("editedValue");
            }

            // Remove any tags
//			$("span.tag", tdTag).remove();
        }
        else if(element["updateType"] == "NewImportDatabaseTableCommandUpdate") {
            $("#DatabaseImportDiv").data("commandId", element["commandId"]);
        }
        else if(element["updateType"] == "SemanticTypesUpdate") {
            var wk = $("div#" + element["worksheetId"]);

            $.each(element["Types"], function(index, type) {
                var tdTag = $("td#" + type["HNodeId"], wk);
                tdTag.data("typesJsonObject", type);
            });
        }
        else if(element["updateType"] == "ImportOntologyCommand") {
            if(!element["Import"])
                $.sticky("Ontology import failed!");
            else
                $.sticky("Ontology successfully imported!");
        }
        else if(element["updateType"] == "TagsUpdate") {
            // Remove all the existing tags
            $("span.tag").remove();
            $.each(element["Tags"], function(index, tag) {
                $.each(tag["Nodes"], function(index2, node) {
                    var cellDiv = $("div#" + node);
                    if(cellDiv.length != 0) {
                        var tagSpanBox = $("<span>").css({
                            backgroundColor : tag["Color"]
                        }).addClass("tag").qtip({
                                content : {
                                    text : tag["Label"]
                                },
                                style : {
                                    classes : 'ui-tooltip-light ui-tooltip-shadow'
                                }
                            });
                        cellDiv.append(tagSpanBox);
                        $(tagSpanBox).show();
                    }
                });
            });
        }
        else if(element["updateType"] == "PublishCSVUpdate") {
            $("a.CSVDownloadLink", titleDiv).remove();
            var titleDiv = $("div#" + element["worksheetId"] + " div.WorksheetTitleDiv");
            // Remove existing link if any
            hideLoading(element["worksheetId"]);
            var downloadLink = $("<a>").attr("href", element["fileUrl"])
                .text("CSV")
                .addClass("CSVDownloadLink  DownloadLink")
                .attr("target", "_blank");
            $("div#WorksheetOptionsDiv", titleDiv).after(downloadLink);
            $.sticky("CSV file published");
        }
        else if(element["updateType"] == "PublishMDBUpdate") {
            $("a.MDBDownloadLink", titleDiv).remove();
            var titleDiv = $("div#" + element["worksheetId"] + " div.WorksheetTitleDiv");
            // Remove existing link if any
            hideLoading(element["worksheetId"]);
            var downloadLink = $("<a>")
                .attr("href", element["fileUrl"])
                .text("ACCESS MDB")
                .addClass("MDBDownloadLink  DownloadLink")
                .attr("target", "_blank");
            $("div#WorksheetOptionsDiv", titleDiv).after(downloadLink);
            $.sticky("MDB file published");
        }
        else if(element["updateType"] == "PublishR2RMLUpdate") {
            // Remove existing link if any
            $("a.R2RMLDownloadLink", titleDiv).remove();

            var titleDiv = $("div#" + element["worksheetId"] + " div.WorksheetTitleDiv");
            hideLoading(element["worksheetId"]);
            var downloadLink = $("<a>").attr("href", element["fileUrl"]).text("R2RML Model").addClass("R2RMLDownloadLink  DownloadLink").attr("target", "_blank");
            $("div#WorksheetOptionsDiv", titleDiv).after(downloadLink);
            $.sticky("R2RML Model published");
        }
        else if(element["updateType"] == "ClearGraph") {
            // Remove existing link if any
            $.sticky("R2RML Model cleared");
        }
        else if(element["updateType"] == "SaveCollection") {
            // Remove existing link if any
            $.sticky("R2RML Model Collection saved");
        }
        else if(element["updateType"] == "PublishSpatialDataUpdate") {
            $("a.SpatialDataDownloadLink", titleDiv).remove();
            var titleDiv = $("div#" + element["worksheetId"] + " div.WorksheetTitleDiv");
            // Remove existing link if any
            hideLoading(element["worksheetId"]);
            var downloadLink = $("<a>").attr("href", element["fileUrl"]).text("SPATIAL DATA").addClass("SpatialDataDownloadLink  DownloadLink").attr("target", "_blank");
            $("div#WorksheetOptionsDiv", titleDiv).after(downloadLink);
            $.sticky("Spatial data published");
        }
        else if(element["updateType"] == "PublishRDFUpdate") {
            var titleDiv = $("div#" + element["worksheetId"] + " div.WorksheetTitleDiv");
            // Remove existing link if any
            $("a.RdfDownloadLink", titleDiv).remove();

            var downloadLink = $("<a>").attr("href", element["fileUrl"]).text("RDF").addClass("RdfDownloadLink  DownloadLink").attr("target", "_blank");
            $("div#WorksheetOptionsDiv", titleDiv).after(downloadLink);

            var errorArr = $.parseJSON(element["errorReport"]);
            if (errorArr && errorArr.length !=0) {
                var errorWindow = $("#karmaErrorWindow");
                var txt = $("#errrorText", errorWindow);
                txt.empty();

                $.each(errorArr, function(index, errorMessage){
                	txt.append("<b>Error # " + (index+1) + "</b><br>");
                	txt.append("<b>Description:</b> " + errorMessage.title + "<br>");
                	txt.append("<b>Reason:</b> " + errorMessage.description + "<br>");
                	txt.append("<hr>")
                });

                errorWindow.modal({keyboard:true, show:true});
            }
        }
        else if(element["updateType"] == "PublishWorksheetHistoryUpdate") {
            var titleDiv = $("div#" + element["worksheetId"] + " div.WorksheetTitleDiv");
            // Remove existing link if any
            $("a.HistoryDownloadLink", titleDiv).remove();

            var downloadLink = $("<a>").attr("href", element["fileUrl"]).text("History").addClass("HistoryDownloadLink DownloadLink").attr("target", "_blank");
            $("div#WorksheetOptionsDiv", titleDiv).after(downloadLink);
        }
        else if(element["updateType"] == "PublishJSONUpdate") {
            var titleDiv = $("div#" + element["worksheetId"] + " div.WorksheetTitleDiv");
            // Remove existing link if any
            $("a.JSONDownloadLink", titleDiv).remove();

            var downloadLink = $("<a>").attr("href", element["fileUrl"]).text("JSON").addClass("JSONDownloadLink DownloadLink").attr("target", "_blank");
            $("div#WorksheetOptionsDiv", titleDiv).after(downloadLink);
        }
        else if(element["updateType"] == "PublishReportUpdate") {
            var titleDiv = $("div#" + element["worksheetId"] + " div.WorksheetTitleDiv");
            // Remove existing link if any
            $("a.ReportDownloadLink", titleDiv).remove();

            var downloadLink = $("<a>").attr("href", element["fileUrl"]).text("Report").addClass("ReportDownloadLink DownloadLink").attr("target", "_blank");
            $("div#WorksheetOptionsDiv", titleDiv).after(downloadLink);
        }
        else if(element["updateType"] == "PublishDatabaseUpdate") {
            if(element["numRowsNotInserted"] == 0) {
                $.sticky("Data saved successfully!");
            } else {
                $.sticky(element["numRowsNotInserted"] + " rows not saved in database! Check log file for details.");
            }
        }
        else if(element["updateType"] == "CleaningResultUpdate") {
            if(element["result"] != null) {
                if(element["result"][0] == null || element["result"][0]["top"].length==0) {
                    alert("Cannot find any transformations! ");
                }
               
                TransformColumnDialog.getInstance().handleCleaningResultUpdate(element["result"]);
            }
        }
        else if(element["updateType"] == "InfoUpdate") {
            $.sticky(element["Info"]);
        }
        else if(element["updateType"] == "AlignmentSVGVisualizationUpdate") {
            // In d3-alignment-vis.js
            displayAlignmentTree_ForceKarmaLayout(element);

        }
        else if(element["updateType"] == "KarmaInfo") {
        	if(infos[element["Info"]]) {
        		//ignore;
        	} else {
	            $.sticky(element["Info"]);
	            
	            infos[element["Info"]] = true;
        	}
        } else if(element["updateType"] == "KarmaTrivialError") {
        	trivialErrors.push(element["TrivialError"]);
        }
        else if(element["updateType"] == "FetchDataMiningModelsUpdate") {

            var modelListRadioBtnGrp = $("#modelListRadioBtnGrp");
            modelListRadioBtnGrp.html('');
            var rows = element["models"]
            if(rows.length == 0) {
            	alert("There are no models available");
            } else {
	            for(var x in rows) {
	                modelListRadioBtnGrp.append('<input type="radio" name="group1" id="model_'+x+'" value="'+rows[x].url+'" /> <label for="model_'+x+'">'+rows[x].name+' ('+rows[x].url+') </label> <br />');
	            }
	            var modelListDiv = $('div#modelListDiv');
	            modelListDiv.dialog({ title: 'Select a service',
	                buttons: { "Cancel": function() { $(this).dialog("close"); }, "Select": submitSelectedModelNameToBeLoaded }, width: 300, height: 150 });
            }
        }
        else if(element["updateType"] == "InvokeDataMiningServiceUpdate") {

        	alert("This results are loaded in a new worksheet");
        }
        else if(element["updateType"] == "CleaningServiceOutput") {
            //console.log(element);
            //console.log(element["hNodeId"]);
            //console.log(element["chartData"]);

            drawChart(element);
            //drawBigChart() ;
        }
        else if(element["updateType"] == "WorksheetCleaningUpdate") {
            var worksheetChartData = element["worksheetChartData"];
            $.each(worksheetChartData, function(index, columnData){
                //console.log(columnData)
                drawChart(columnData);
            });
        }
        else if(element["updateType"] == "AdditionalRowsUpdate") {
            var dataTable = $("div#" + element.tableId + " > table.wk-table");
            addWorksheetDataRecurse(element["worksheetId"], element.rows, dataTable, true);

            // Update the number of additional records left
            var additionalRecordsLeft = element.additionalRowsCount;
            var loadMoreLink = $("div#" + element.tableId +  " > div.load-more > a");

            if (additionalRecordsLeft == 0) {
                loadMoreLink.remove();
                $("div#" + element.tableId +  " > div.load-more").remove();
            } else {
                loadMoreLink.text(additionalRecordsLeft + " additional records, load more...");
            }
        }
        else if(element["updateType"] == "ExportCSVUpdate") {
            // Remove existing link if any
        	var titleDiv = $("div#" + element["worksheetId"] + " div.WorksheetTitleDiv");
            $("a.CSVExportDownloadLink", titleDiv).remove();

            hideLoading(element["worksheetId"]);
            var downloadLink = $("<a>").attr("href", element["fileUrl"]).text("CSV Export").addClass("CSVExportDownloadLink  DownloadLink").attr("target", "_blank");
            $("div#WorksheetOptionsDiv", titleDiv).after(downloadLink);
            $.sticky("CSV exported");
        }
        
    });
    
    if(trivialErrors.length > 0) {
       var errorWindow = $("#karmaErrorWindow");
       var txt = $("#errrorText", errorWindow);
       txt.empty();

       var errExists = [];
       if(trivialErrors.length == 1) {
    	   errorMessage = trivialErrors[0];
    	   txt.append(errorMessage + "<br>");
    	   errExists[errorMessage] = true;
       } else {
	        $.each(trivialErrors, function(index, errorMessage) {
	        	if(errExists[errorMessage]) {
	        		//do nothing
	        	} else {
	        		txt.append("<b>Error # " + (index+1) + "</b><br>");
	        		txt.append("<b>Description:</b> " + errorMessage + "<br>");
	        		txt.append("<hr>");
	        		errExists[errorMessage] = true;
	        	}
	        });
       }
       
        errorWindow.modal({keyboard:true, show:true});
        
    }
}

function addColumnHeadersRecurse(worksheetId, columns, headersTable, isOdd) {
    var row = $("<tr>");
    if (isOdd) {
        row.addClass("wk-row-odd");
    } else {
        row.addClass("wk-row-even");
    }

    var columnWidths = [];
    $.each (columns, function (index, column) {

        var td = $("<td>").addClass("wk-header-cell").attr("id", column.hNodeId);
        var headerDiv = $("<div>").addClass(column["columnClass"]);

        var colWidthNumber = 0;
        if (column["pythonTransformation"])
        {
        	td.data("pythonTransformation", column["pythonTransformation"]);
        }
        if (column["previousCommandId"])
        {
        	td.data("previousCommandId", column["previousCommandId"]);
        }
        if (column["columnDerivedFrom"])
        {
        	td.data("columnDerivedFrom", column["columnDerivedFrom"]);
        }
        
        if (column["hasNestedTable"]) {
            var pElem = $("<div>")
            				.addClass("wk-header")
            				.addClass("wk-subtable-header")
//            				.text(column["columnName"])
//            				.mouseenter(showColumnOptionButton)
//            				.mouseleave(hideColumnOptionButton);
            				.append((new TableColumnOptions(worksheetId, column.hNodeId, column["columnName"], false)).generateJS());

            var nestedTableContainer = $("<div>").addClass("table-container");
            var nestedTableHeaderContainer = $("<div>").addClass("table-header-container");
            var nestedTable = $("<table>").addClass("wk-table");
            if (isOdd) {
                nestedTable.addClass("htable-even");
            } else {
                nestedTable.addClass("htable-odd");
            }
            var nestedColumnWidths = addColumnHeadersRecurse(worksheetId, column["columns"], nestedTable, !isOdd);

            var colAdded = 0;
            $.each(nestedColumnWidths, function(index2, colWidth){
                if (!colWidth.widthAddedToNestedTable) {
                    colWidthNumber += colWidth.width;
                    colWidth.widthAddedToNestedTable = true;
                    colAdded++;
                }
                columnWidths.push(colWidth);
            });
            // Add padding for nested cells
            colWidthNumber += (colAdded * 2 * 9);
            // Add border width
            colWidthNumber += (colAdded + 1);

            headerDiv.append(pElem).append(nestedTableContainer.append(nestedTableHeaderContainer.append(nestedTable)));
        } else {
            headerDiv.addClass("wk-header")
            	//.text(column["columnName"]).mouseenter(showColumnOptionButton).mouseleave(hideColumnOptionButton);
            	.append((new TableColumnOptions(worksheetId, column.hNodeId, column["columnName"], true)).generateJS());
            // Pedro: limit cells to 30 chars wide. This should be smarter: if the table is not too wide, then allow more character.
            // If we impose the limit, we should set the CSS to wrap rather than use ... ellipsis.
            // We will need a smarter data structure so we can do two passes, first to compute the desired lenghts based on number of characters
            // and then revisit to assign widths based on total demand for space.
            var effectiveCharacterLength = Math.min(column.characterLength, 30);
            //colWidthNumber = Math.floor(column.characterLength * 12 * 0.75);
            colWidthNumber = Math.floor(effectiveCharacterLength * 12 * 0.75);
            // Pedro: columns for URIs are often too wide. Need a smarter way to limit them.
            // Would be good to narrow based on total space rather than unilateraly like the following statement.
            colWidthNumber = Math.min(colWidthNumber,130);
        }

        var colWidth = {};
        colWidth.columnClass = column["columnClass"];
        colWidth.width = colWidthNumber;
        colWidth.widthAddedToNestedTable = false;
        columnWidths.push(colWidth);

        row.append(td.append(headerDiv));
    });
    headersTable.append(row);
    return columnWidths;
}

function addWorksheetDataRecurse(worksheetId, rows, dataTable, isOdd) {
    // Loop through the rows
    $.each (rows, function (index, row) {
        var rowTr = $("<tr>");
        if (isOdd) {
            rowTr.addClass("wk-row-odd");
        } else {
            rowTr.addClass("wk-row-even");
        }
        /*var dataDiv = $("<div>").width(30).height(30);
        var checkbox = $('<input />', { type: 'checkbox', id: 'selectcolumns', value: "a" });
        dataDiv.append(checkbox);
        rowTr.append(dataDiv);*/
        //console.log(.css(rows[0][0]["columnClass"]));
        // Loop through the values in a given row
        //console.log(rows);
        $.each(row, function(index2, cell){
            var td = $("<td>").addClass("wk-cell");
            var dataDiv = $("<div>");

            if (cell["hasNestedTable"]) {
                var nestedTableDataContainer = $("<div>").addClass("table-data-container").attr("id", cell["tableId"]);
                var nestedTable = $("<table>").addClass("wk-table");

                addWorksheetDataRecurse(worksheetId, cell["nestedRows"], nestedTable, !isOdd);

                var additionalRowsAvail = cell["additionalRowsCount"];

                var moreRowsDiv = $("<div>").addClass("load-more");
                if (additionalRowsAvail != 0) {
                    var moreRowsLink = $("<a>").attr("href","#!").text(additionalRowsAvail +
                        " additional records, load more...").click(loadAdditionalRowsHandler);
                    moreRowsDiv.append(moreRowsLink);
                    nestedTableDataContainer.append(nestedTable).append(moreRowsDiv);
                } else {
                    nestedTableDataContainer.append(nestedTable);
                }
                /*if (index2 == 0) {
                    var dataDiv2 = $("<div>").width(30).height(30);
                    var checkbox = $('<input />', { type: 'checkbox', class: 'selectRowID', value: cell["rowID"] });
                    dataDiv2.append(checkbox);
                    dataDiv.append(dataDiv2); 
                }*/
                dataDiv.append(nestedTableDataContainer);
                dataDiv.addClass(cell["columnClass"]);
            } else {
                var dataDiv3 = $("<div>").addClass("wk-value");
                //console.log(stylesheet)
                dataDiv.addClass(cell["columnClass"]);
                dataDiv3.text(cell["displayValue"])
                    .attr('id', cell["nodeId"])
                    .data("expandedValue", cell["expandedValue"])
                    .attr("title", cell["expandedValue"]) //for tooltip
                    ;
                
        		dataDiv3.editable({
        			 type: 'text',
        			 success: function(response, newValue) {
        				 console.log("Set new value:" + newValue);
        				 submitTableCellEdit(worksheetId, cell["nodeId"], newValue);
        			 },
        			 showbuttons: 'bottom',
        			 mode: 'popup',
        			 inputclass: 'worksheetInputEdit'	 
		            });
                /*if (index2 == 0) {
                    var stylesheet = document.styleSheets[0];
                    var width = 0;
                    for (i = 0; i < stylesheet.cssRules.length; i++) {
                        if (stylesheet.cssRules[i].selectorText == "." + cell["columnClass"])
                            var str = stylesheet.cssRules[i].style['width'];
                            if (typeof(str) == "string") {
                                str = str.substring(0, str.indexOf("px"));
                                //console.log(str);
                                width = parseInt(str);
                                break;
                            }

                    }
                    console.log(width);
                    dataDiv3.css({width: width - 30, float: "right"});
                    var dataDiv2 = $("<div>").width(30).height(30).css({float:'left'});
                    var checkbox = $('<input />', { type: 'checkbox', class: 'selectRowID', value: cell["rowID"] });
                    dataDiv2.append(checkbox);
                    dataDiv.append(dataDiv2); 
                } */
                dataDiv.append(dataDiv3);
                td.addClass(cell["columnClass"]); 
            }
            rowTr.append(td.append(dataDiv));
        });

        dataTable.append(rowTr);
    });
    return;
}

function submitTableCellEdit(worksheetId, nodeId, value) {
    var edits = new Object();
    edits["value"] = value;
    edits["command"] = "EditCellCommand";
    edits["nodeId"] = nodeId;
    edits["worksheetId"] = worksheetId;
    edits["workspaceId"] = $.workspaceGlobalInformation.id;

    var returned = $.ajax({
        url: "RequestController",
        type: "POST",
        data : edits,
        dataType : "json",
        complete :
            function (xhr, textStatus) {
                var json = $.parseJSON(xhr.responseText);
                parse(json);
            }
    });
}


function submitSelectedModelNameToBeLoaded() {
    $('div#modelListDiv').dialog("close");
    var optionsDiv = $("div#WorksheetOptionsDiv");
    var value = $("#modelListRadioBtnGrp").find("input:checked");

    var info = new Object();
    info["worksheetId"] = optionsDiv.data("worksheetId");
    info["workspaceId"] = $.workspaceGlobalInformation.id;
    info["command"] = "InvokeDataMiningServiceCommand";
    info['modelContext'] = value.val();
    info['dataMiningURL'] = value.attr('rel');


    var returned = $.ajax({
        url: "RequestController",
        type: "POST",
        data : info,
        dataType : "json",
        complete :
            function (xhr, textStatus) {
                var json = $.parseJSON(xhr.responseText);
                parse(json);
            },
        error :
            function (xhr, textStatus) {
                alert("Error occured while invoking the selected service!" + textStatus);
            }
    });
}
