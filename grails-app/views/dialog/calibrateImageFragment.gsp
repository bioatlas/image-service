<div class="form-horizontal">
    <div class="control-group">
        How long is the line you just drew on the image?
    </div>
    <div class="control-group">
        <div class="controls">
            <g:textField class="input-small" name="pixelLength" value="${pixelLength}"/> pixels =
            <g:textField class="input-small" name="mmLength" value="" />
            <g:select name="units" class="input-small" from="${['mm','inches', 'metres','feet']}" value="mm"/>
        </div>
    </div>
    <div class="control-group">
        <div class="controls">
            <button class="btn btn-primary" id="btnCalibrateImageScale">Save</button>
            <button class="btn" id="btnCancelCalibrateImageScale">Cancel</button>
        </div>
    </div>
</div>
<script>

    $("#btnCancelCalibrateImageScale").click(function(e) {
        e.preventDefault();
        imgvwr.hideModal();
    });

    $("#btnCalibrateImageScale").click(function(e) {
        e.preventDefault();
        var units = $("#units").val();
        var pixelLength = $("#pixelLength").val();
        var actualLength = $("#mmLength").val();
        $.ajax("${grailsApplication.config.grails.serverURL}${createLink(controller:'webService', action:'calibrateImageScale', params:[imageId: imageInstance.imageIdentifier])}&units=" + units + "&pixelLength=" + pixelLength + "&actualLength=" + actualLength).done(function() {
            imgvwr.hideModal();
        });

        <g:if test="${params.callback}">
        ${params.callback}(pixelLength);
        </g:if>
    });

</script>