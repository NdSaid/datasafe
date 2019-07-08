import {Component, OnInit} from '@angular/core';
import {CredentialsService} from "../credentials.service";
import {ApiService} from "../api.service";
import {Router} from "@angular/router";

@Component({
  selector: 'app-user',
  templateUrl: './user.component.html',
  styleUrls: ['./user.component.css']
})
export class UserComponent implements OnInit {

  error: string;

  constructor(private creds: CredentialsService, private api: ApiService, private router: Router) { }

  ngOnInit() {
    if (null == this.creds.getCredentialsForApi()) {
      this.router.navigate([''])
    }
  }
}