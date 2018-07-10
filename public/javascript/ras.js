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

$(function() {

    $('#errors').focus();

    var pageTitle = $('title').text();

    $("#get-help-action").click(function() {
        ga('send', 'event', 'link - click', pageTitle, 'get help with this page');
    });

    $("#full-width-banner-no-thanks").click(function(){
        $('.full-width-banner').fadeOut('slow');
        return true;
    });

    $('button[id=continue]').click(function() {
        var label = $('input:radio[name=userChoice]:checked').val();
        if (typeof label !== 'undefined') {
            ga('send', 'event', 'button - click', 'What do you want to do', 'Continue - ' + label);
        }
    });

});


