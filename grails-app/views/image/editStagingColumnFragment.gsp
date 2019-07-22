<%@ page import="au.org.ala.images.StagingColumnType; au.org.ala.images.StagingColumnDefinition; au.org.ala.images.DarwinCoreField" %>
<div class="form-horizontal">

    <div class="control-group">
        <label class="control-label" for="fieldType">Field type</label>
        <div class="controls">
            <g:select class="fieldType" name="fieldType" from="${StagingColumnType.values()}" value="${fieldDefinition?.fieldDefinitionType}"/>
            <img:helpText tooltipPosition="topMiddle" targetPosition="bottomMiddle" tipPosition="topMiddle" width="600">
                <ul>
                    <li><code>NameRegex</code> extracts part of the filename out using a <em>regular expression</em> and a capturing group.</li>
                    <li><code>Literal</code> to assign the same static field value to every task</li>
                    <li><code>DataFileColumn</code> assigns a value from an uploaded csv file</li>
                </ul>
            </img:helpText>
        </div>
    </div>

    <div id="formatBlock">
        <div class="control-group">
            <label class="control-label" for="definition" id="formatLabel">Definition/Value</label>
            <div class="controls">
                <g:textField name="definition" value="${fieldDefinition?.format}" />

                <g:if test="${hasDataFile && dataFileColumns}">
                    <g:select name="dataFileColumn" from="${dataFileColumns}" value="${fieldDefinition?.format}" />
                </g:if>
            </div>
        </div>
    </div>

    <div class="control-group">
        <label class="control-label" for="fieldName">Field name</label>
        <div class="controls">
            <g:textField name="fieldName" value="${fieldDefinition?.fieldName}" />
        </div>
    </div>

    <div class="control-group">

        <div class="controls">
            <button class="btn" id="btnCancelEditFieldDefinition">Cancel</button>
            <button class="btn btn-primary" id="btnSaveFieldDefinition">Save</button>
        </div>
    </div>

    <script>

        jQuery("#fieldName").autocomplete("${grailsApplication.config.grails.serverURL}${createLink(controller:'webService', action:'darwinCoreTerms')}", {
            dataType: 'jsonp',
            parse: function (data) {
                var rows = new Array();
                for (var i = 0; i < data.length; i++) {
                    rows[i] = {
                        data: data[i],
                        value: data[i].name,
                        result: data[i].name
                    };
                }
                return rows;
            },
            matchSubset: false,
            formatItem: function (row, i, n) {
                return row.name;
            },
            minChars: 2,
            scroll: false,
            selectFirst: false
        });

        $("#btnCancelEditFieldDefinition").click(function(e) {
            e.preventDefault();
            imgvwr.hideModal();
        });

        $("#btnSaveFieldDefinition").click(function(e) {
            e.preventDefault();
            var fieldName = encodeURIComponent($("#fieldName").val());
            var fieldType = encodeURIComponent($("#fieldType").val());
            var format = encodeURIComponent($("#definition").val());

            if (fieldName) {
                window.location = "${createLink(controller:'image', action:'saveStagingColumnDefinition', params:[columnDefinitionId: fieldDefinition?.id])}&fieldName=" + fieldName + "&fieldType=" + fieldType + "&definition=" + format;
            }
        });

        $("#fieldType").change(function() {
            updateFormatOptions();
        });

        $("#dataFileColumn").change(function() {
            var value = $("#dataFileColumn").val();
            $("#fieldName").val(value);
            $("#definition").val(value);
        });

        function updateFormatOptions() {
            var fieldType = $("#fieldType").val();
            $("#definition").css("display", "block");
            $("#dataFileColumn").css("display", "none");

            if (fieldType == 'Sequence') {
                $("#formatBlock").css('display', 'none');
            } else {
                $("#formatBlock").css('display', 'block');
                if (fieldType == 'NameRegex') {
                    $("#formatLabel").html("Expression")
                } else if (fieldType == 'NamePattern') {
                    $("#formatLabel").html("Pattern")
                } else if (fieldType == 'DataFileColumn') {
                    $("#formatLabel").html("Column (leave blank to use field name)")
                    <g:if test="${hasDataFile && dataFileColumns}">
                    $("#definition").css("display", "none");
                    $("#dataFileColumn").css("display", "block");
                    </g:if>
                } else {
                    $("#formatLabel").html("Value")
                }
            }
        }

        updateFormatOptions();

    </script>

</div>