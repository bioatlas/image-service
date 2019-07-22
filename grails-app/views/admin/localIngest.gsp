<%@ page import="au.org.ala.cas.util.AuthenticationUtils" %>
<!doctype html>
<html>
    <head>
        <meta name="layout" content="adminLayout"/>
        <title>ALA Images - Admin - Local Ingestion</title>
        <style type="text/css" media="screen">
        </style>
    </head>

    <body>
        <content tag="pageTitle">Tools</content>
        <content tag="adminButtonBar" />
        <div class="">
            <h4>Image Metadata</h4>
            <p>Add field definitions here to attach meta data to each image as it is ingested into the image service.</p>
            <button class="btn btn-small btn-success" id="btnAddField"><i class="icon-plus icon-white"></i>&nbsp;Add Field</button>
            <div class="" id="fieldDefinitions" style="margin-top: 5px"></div>
        </div>

        <div class="well well-small">
            <button class="btn btn-primary" id="btnStartImageImport">Start Import</button>
            <div id="startMessage"></div>
        </div>
        <div class="">
            <h4>File List</h4>
            <div id="fileList">

            </div>
        </div>
        <table>
            <tr></tr>
        </table>
    </body>
    <script>

        $(document).ready(function() {

            $("#btnStartImageImport").click(function(e) {
                e.preventDefault();
                $.ajax("${createLink(controller:'webService', action:'scheduleInboxPoll', params: [userId:AuthenticationUtils.getUserId(request)])}").done(function(results) {
                    $("#startMessage").html('<div class="alert alert-info">Import started with batch id ' + results.importBatchId + '</div>' );
                });

            });

            $("#btnAddField").click(function(e) {
                e.preventDefault();
                var options = {
                    title:"Add Field Definition",
                    url: "${createLink(action:'addFieldFragment')}",
                    onClose: function() {
                        renderFieldDefinitions();
                        renderFileList();
                    }
                };

                imgvwr.showModal(options);
            });

            renderFieldDefinitions();
            renderFileList();

        });

        function renderFieldDefinitions() {
            $.ajax("${createLink(action:"fieldDefinitionsFragment")}").done(function(content) {
                $("#fieldDefinitions").html(content);
                // Delete buttons
                $(".btnDeleteFieldDefinition").click(function(e) {
                    e.preventDefault();
                    var fieldId = $(this).closest("[fieldDefinitionId]").attr("fieldDefinitionId");
                    if (fieldId) {
                        $.ajax("${createLink(action:'deleteFieldDefinition')}/" + fieldId).done(function() {
                            renderFieldDefinitions();
                            renderFileList();
                        });
                    }
                });
                // Edit buttons
                $(".btnEditFieldDefinition").click(function(e) {
                    e.preventDefault();
                    var fieldId = $(this).closest("[fieldDefinitionId]").attr("fieldDefinitionId");
                    if (fieldId) {
                        var options = {
                            title:"Add Field Definition",
                            url: "${createLink(action:'editFieldFragment')}/" + fieldId,
                            onClose: function() {
                                renderFieldDefinitions();
                                renderFileList();
                            }
                        };

                        imgvwr.showModal(options);
                    }
                });


            });
        }

        function renderFileList() {
            $.ajax("${createLink(action:"inboxFileListFragment")}").done(function(content) {
                $("#fileList").html(content);
            });
        }

    </script>
</html>
