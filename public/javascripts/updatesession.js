var cancel = false;
var uploading = false;
var redirect = true;
var newVideoURL = "";

Dropzone.autoDiscover = false;

window.onbeforeunload = function() {
    if(!redirect) {
        return 'The file upload is still going on. If you leave the page now your upload' +
        'will be cancelled. Are you sure you want to leave the page?';
    }
}

$(function () {
    var sessionId = $('input[name^="sessionId"]').val();

    var youtubeDropzone = new Dropzone("#youtubeVideo", {
        url: "/youtube/" + sessionId + "/upload",
        maxFilesize: 2048,
        dictDefaultMessage: "Drop your file here to upload(or click)",
        uploadMultiple: false,
        headers: {
            'CSRF-Token': document.getElementById('csrfToken').value
        },
        autoProcessQueue: false
    });

    youtubeDropzone.on("sending", function(file, xhr, formData) {
        redirect = false;
        console.log("File size = " + file.size);
        xhr.setRequestHeader("filesize", file.size);
        xhr.setRequestHeader("title", $("#youtube-title").val());
        xhr.setRequestHeader("description", $("#youtube-description").val());
        xhr.setRequestHeader("tags", $("#youtube-tags").val());
        xhr.setRequestHeader("category", $("#youtube-category").val());
        xhr.setRequestHeader("status", $("#youtube-status").val());
    });

    youtubeDropzone.on("complete", function(file) {
        redirect = true;
        uploading = true;
        $("#cancel-message").hide();
        console.log("File uploading completed");
    });

    youtubeDropzone.on("success", function(file, response) {
        console.log("Showing progress now");
        $("#show-progress").show();
        $("#cancel-video-button").show();
        cancel = false;
        $("#youtube-dropzone").hide();
        showProgress(sessionId);
    });

    console.log("sessionId = " + sessionId);
    $("#upload-success-message").hide();
    $("#cancel-video-button").hide();

    jsRoutes.controllers.YoutubeController.getPercentageUploaded(sessionId).ajax(
        {
            type: 'GET',
            processData: false,
            contentType: false,
            success: function (data) {
            console.log("Coming here");
                $("#upload-success-message").hide();
                $("#already-upload").hide();
                $("#no-upload-cancel").hide();
                $("#cancel-message").hide();
                $("#show-progress").show();
                $("#cancel-video-button").show();
                $("#youtube-dropzone").hide();
                uploading = true;
                showProgress(sessionId);
            }
        });

    $("#cancelVideo").click( function () {
        if(uploading) {
            cancel = true;
            uploading = false;
            $("#upload-success-message").hide();
            cancelVideo(sessionId);
        } else {
            $("#upload-success-message").hide();
            $("#cancel-message").hide();
            $("#no-upload-cancel").show();
        }
    });

    $("#updateVideo").click( function () {
        update(sessionId);
    });

    $(".bootstrap-tagsinput").addClass('update-field');

    $(".bootstrap-tagsinput input").keypress(function (event) {
        if (event.keyCode == 13) {
            event.preventDefault();
        }
    });

    $("#uploadVideo").click( function () {
        youtubeDropzone.processQueue();
        $("#upload-video-button").hide();
    });

    $("#attach-video-link").click(function () {
        $("#youtubeURL").val("www.youtube.com/embed/" + newVideoURL);
    });

});

function showProgress(sessionId) {
    jsRoutes.controllers.YoutubeController.getPercentageUploaded(sessionId).ajax(
        {
            type: 'GET',
            processData: false,
            contentType: false,
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;

                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function(data) {
                if(!cancel) {
                    if(data == 100) {
                        $("#upload-success-message").show();
                        $("#show-progress").hide();
                        $("#progress").width('0%');
                        $("#progress").text('0%');
                        uploading = false;
                        $("#youtube-dropzone").show();
                        $("#cancel-video-button").hide();
                        getUpdateURL(sessionId);
                    } else {
                    var percentageUploaded = data;
                    console.log("percentageUploaded = " + percentageUploaded);
                    $("#progress").width(percentageUploaded + '%');
                    $("#progress").text(Math.ceil(percentageUploaded) * 1  + '%');
                    showProgress(sessionId);
                    }
                }
            },
            error: function(er) {
                console.log("showProgress failed with error = " + er);
            }
        });
}

function cancelVideo(sessionId) {

    jsRoutes.controllers.YoutubeController.cancel(sessionId).ajax(
        {
            type: 'GET',
            processData: false,
            contentType: false,
            success: function(data) {
                $("#upload-success-message").hide();
                $("#upload-failure-message").hide();
                $("#show-progress").hide();
                $("#progress").width('0%');
                $("#progress").text('0%');
                $("#cancel-message").show();
                $("#youtube-dropzone").show();
                $("#cancel-video-button").hide();
                $("#upload-video-button").show();
            },
            error: function(er) {
                $("#upload-success-message").hide();
                $("#upload-failure-message").hide();
                $("#show-progress").hide();
                $("#progress").width('0%');
                $("#progress").text('0%');
                $("#cancel-message").show();
            }
        })
}

function getUpdateURL(sessionId) {
    console.log("Coming inside the function of getUpdateURL");
    jsRoutes.controllers.YoutubeController.getVideoId(sessionId).ajax(
        {
            type: 'GET',
            processData: false,
            contentType: false,
            success: function(data) {
                newVideoURL = data;
                console.log("Setting embedded URL for youtube");
                $("#attach-video").show();
            },
            error: function(er) {
                console.log("An error was encountered = " + er);
            }
        });
}

function getUrl(file) {
    var fileSize = file.size;
    var url = "/youtube/" + sessionId + "/" + fileSize + "/upload";
    return url;
}

function update(sessionId) {
    var title = $("#youtube-title").val();
    var description = $("#youtube-description").val();
    var tags = $("#youtube-tags").val().split(",");
    var status = $("#youtube-status").val();
    var category = $("#youtube-category").val();

    var formData = {
        "title": title,
        "description": description,
        "tags": tags,
        "status": status,
        "category": category
    };

    jsRoutes.controllers.YoutubeController.updateVideo(sessionId).ajax(
        {
            type: 'POST',
            processData: false,
            contentType: 'application/json',
            data: JSON.stringify(formData),
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;

                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function (data) {
                $("#successful-update").show();
                console.log("Successfully Completed & data received was = " + data);
            },
            error: function(er) {
                $("#unsuccessful-update").show();
                console.log("Error occurred: " + er);
            }
        });

    console.log("title = " + title);
    console.log("description = " + description);
    console.log("tags = " + tags);
    console.log("status = " + status);
}
