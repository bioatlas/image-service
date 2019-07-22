<%@ page import="au.org.ala.images.MetaDataSourceType" %>
<div>

    <form>
        <div class="form-group">
            <label class="control-label" for="metaDataKey">Name:</label>
            <input type="text" class="form-control input-lg" id="metaDataKey" placeholder="Metadata key">
        </div>

        <div class="form-group">
            <label class="control-label" for="metaDataValue">Value:</label>
            <input type="text" class="form-control input-lg" id="metaDataValue" placeholder="Value">
        </div>

        <div class="form-group">
            <button class="btn btn-primary" id="btnAddNewUserMetadata">Add</button>
            <button class="btn btn-default" id="btnCancelAddUserMetaData">Cancel</button>
        </div>
    </form>

    <script>

        $("#btnCancelAddUserMetaData").click(function(e) {
            e.preventDefault();
            imgvwr.hideModal();
        });

        $("#btnAddNewUserMetadata").click(function(e) {
            e.preventDefault();
            var key = $("#metaDataKey").val();
            var value = $("#metaDataValue").val();
            if (key && value) {
                key = encodeURIComponent(key);
                value = encodeURIComponent(value);
                if (imgvwr.onAddMetadata) {
                    imgvwr.onAddMetadata(key, value);
                }
            }
        });
    </script>
</div>
