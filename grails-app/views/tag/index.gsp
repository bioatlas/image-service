<!doctype html>
<html>
<head>
    <meta name="layout" content="main"/>
    <meta name="section" content="home"/>
    <title>Tags | ${grailsApplication.config.skin.orgNameLong}</title>

    <style>
    </style>
    <r:require module="bootstrap" />
    <r:require module="jstree" />

</head>

<body class="content">
<img:headerContent title="Tags">
    <%
        pageScope.crumbs = [
        ]
    %>
</img:headerContent>
<div class="row-fluid">
    <div class="col-md-4">
        <div class="well well-small">

            <div class="row-fluid" style="margin-bottom: 10px">
                <div class="col-md-12">
                    <button class="btn btn-success" id="btnCreateNewTag"><i class="icon-plus icon-white"></i>&nbsp;Add tag</button>
                    <button class="btn btn-default" id="btnRenameSelectedTag">Rename tag</button>
                    <button class="btn btn-danger" id="btnDeleteSelectedTag"><i class="icon-remove icon-white"></i>&nbsp;Delete tag</button>
                </div>
            </div>

            <div class="row-fluid">
                <div class="col-md-12">
                    <div id="tagContainer">
                        <img:spinner />
                    </div>
                </div>
            </div>
        </div>
    </div>
    <div class="col-md-8">
        <div class="well well-small">
        </div>
    </div>
</div>
</body>
</html>

<script>

    $(document).ready(function() {

        $("#btnCreateNewTag").click(function(e) {
            e.preventDefault();
            createTag();
        });

        $("#btnRenameSelectedTag").click(function(e) {
            e.preventDefault();
            renameSelectedTag();
        });

        $("#btnDeleteSelectedTag").click(function(e) {
            e.preventDefault();
            deleteSelectedTag();
        });

        loadTagTree();

    });

    function getSelectedTagId() {
        var tree = $("#tagTree");
        var selected = tree.jstree("get_selected");
        if (selected && selected.length > 0) {
            var selectedNode = tree.jstree("get_node", selected[0]);
            return selectedNode.original.tagId;
        }
        return null
    }

    function deleteSelectedTag() {
        var tagId = getSelectedTagId();
        if (tagId) {
            var opts = {
                title:"Delete tag",
                url: "${createLink(controller:'tag', action:'deleteTagFragment')}?tagId=" +tagId,
                onClose: function() {
                    loadTagTree();
                }
            }
            imgvwr.showModal(opts);
        }
    }

    function renameSelectedTag() {
        var tagId = getSelectedTagId();
        if (tagId) {
            var opts = {
                title:"Rename tag",
                url: "${createLink(controller:'tag', action:'renameTagFragment')}?tagId=" +tagId,
                onClose: function() {
                    loadTagTree();
                }
            }
            imgvwr.showModal(opts);
        }
    }

    function createTag() {
        var parentTagId = getSelectedTagId();
        var opts = {
            title:"Create tag",
            url: "${createLink(controller:'tag', action:'createTagFragment')}?parentTagId=" + parentTagId,
            onClose: function() {
                loadTagTree();
            }
        }
        imgvwr.showModal(opts);
    }

    function loadTagTree() {

        $("#tagContainer").html('<div id="tagTree"></div>');

        $.ajax("${createLink(controller:'webService', action:'getTagModel')}").done(function(rootNodes) {

            var tree = $("#tagTree");

            tree.on("ready.jstree", function (event, data) {
                tree.jstree("open_all");
            }).on("move_node.jstree", function(e, data) {

                var newParentTagId = -1;
                if (data.parent) {
                    var parentNode = tree.jstree("get_node", data.parent);
                    if (parentNode && parentNode.original) {
                        newParentTagId = parentNode.original.tagId;
                    }
                }

                var targetTagId = data.node.original.tagId;
                moveTag(targetTagId, newParentTagId);
            }).jstree({
                "plugins" : ["dnd", "checkbox"],
                "core" : {
                    "animation" : 100,
                    data: rootNodes,
                    "themes": {
                        dots: false
                    },
                    "check_callback" : true,
                    multiple: false
                },
                "checkbox" : {
                    three_state: false,
                    whole_node: false
                },
                "dnd" : {
                    check_while_dragging: false
                }
            });

        });
    }

    function moveTag(targetTagId, newParentTagId) {
        var url = "${createLink(controller:'webService', action:'moveTag')}?targetTagId=" + targetTagId + "&newParentTagId=" + newParentTagId;
        $.ajax(url).done(function() {
            loadTagTree();
        });

    }

</script>

