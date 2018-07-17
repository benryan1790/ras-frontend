/*
* Copyright 2016 HM Revenue & Customs
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

function checkFileType(form) {

    //scroll to top of page
    document.body.scrollTop = document.documentElement.scrollTop = 0;

    var arrInputs = form.getElementsByTagName("input");
    var input = arrInputs[0];
    var fileName = input.value;

    if (fileName.length > 0) {
        if (fileName.substr(fileName.length - ".csv".length, ".csv".length).toLowerCase() == ".csv") {
            if (hasErrors()) {
                showError(reportedError());
                return false;
            }
            else {
                return true;
            }
        }
        else {
            showError('File must be a CSV');
            return false;
        }
    }
    else {
        showError('Select a CSV');
        return false;
    }
}

function reportedError() {
    return $('#temp-error').val();
}

function reportError(message) {
    $('#temp-error').val(message);
}

function cleanError() {
    $('#temp-error').val("");
}

function hasErrors() {
    return $('#temp-error').val() != "";
}

function showError(message){
    $('.validation-summary').show();
    $('#error').html(message);
    $('#file-upload').addClass("form-field--error");
    $('#upload-error').empty();
    $('#upload-error').html(message);
    $('#errors').focus();
    ga("send", "event", "There is a problem - view", "Upload a file", message);
}

$(function() {
    var errors = $('#errors');
    if(errors.length > 0 && errors.css('display') !== 'none') {
        $('#errors').focus();
        var errorLink = $('.error-summary-list li a');
        if (errorLink.length > 0) {
            ga("send", "event", "There is a problem - view", "Upload a file", errorLink.text());
        }
    }
});