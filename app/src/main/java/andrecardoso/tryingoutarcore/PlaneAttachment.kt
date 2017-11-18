/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package andrecardoso.tryingoutarcore

import com.google.ar.core.Anchor
import com.google.ar.core.Plane
import com.google.ar.core.Pose

/**
 * This class tracks the attachment of object's Anchor to a Plane. It will construct a pose
 * that will stay on the plane (in Y direction), while still properly tracking the XZ changes
 * from the anchor updates.
 */
class PlaneAttachment(private val plane: Plane, val anchor: Anchor) {

  // Allocate temporary storage to avoid multiple allocations per frame.
  private val poseTranslation = FloatArray(3)
  private val poseRotation = FloatArray(4)

  val isTracking: Boolean
    get() = plane.trackingState == Plane.TrackingState.TRACKING && anchor.trackingState == Anchor.TrackingState.TRACKING

  val pose: Pose
    get() {
      val pose = anchor.pose
      pose.getTranslation(poseTranslation, 0)
      pose.getRotationQuaternion(poseRotation, 0)
      poseTranslation[1] = plane.centerPose.ty()
      return Pose(poseTranslation, poseRotation)
    }
}
