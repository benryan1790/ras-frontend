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

$.timeoutDialog({
   timeout: 780,
   countdown: 120,
   keepAliveUrl: '/keep-alive',
   logout_url: '/relief-at-source/signed-out',
   title: 'You’re about to be signed out',
   message: 'For security reasons, you will be signed out of this service in',
   close_on_escape: true,
   background_no_scroll: true,
   keep_alive_button_text: 'Stay signed in',
   sign_out_button_text: 'Sign out'
});